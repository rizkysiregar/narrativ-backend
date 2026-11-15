package com.rizkysiregar.feedzmore.messaging.consumer;

import com.rizkysiregar.feedzmore.config.RabbitMQConfig;
import com.rizkysiregar.feedzmore.messaging.event.VideoThumbnailGeneratedEvent;
import com.rizkysiregar.feedzmore.messaging.event.VideoThumbnailRequestedEvent;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.entity.PostMediaEntity;
import com.rizkysiregar.feedzmore.model.enums.MediaType;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.repository.PostMediaRepository;
import com.rizkysiregar.feedzmore.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Stores the thumbnail produced by the media worker on the post media. The update is
 * idempotent (same media, same key), so redelivery needs no deduplication.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VideoThumbnailResultConsumer {

    private final PostCrudRepository postCrudRepository;
    private final PostMediaRepository postMediaRepository;
    private final FileStorageService fileStorageService;

    @RabbitListener(queues = RabbitMQConfig.VIDEO_THUMBNAIL_RESULT_QUEUE)
    public void handleThumbnailGenerated(VideoThumbnailGeneratedEvent event) {
        if (event.getPostId() == null || event.getThumbnailObjectKey() == null) {
            log.warn("THUMBNAIL.IGNORED reason=missing_fields postId={} key={}", event.getPostId(), event.getThumbnailObjectKey());
            return;
        }

        Optional<PostEntity> found = postCrudRepository.findById(event.getPostId());
        if (found.isEmpty() || found.get().isDeleted()) {
            // The post was removed while the worker was busy: don't leave an orphan object behind.
            log.info("THUMBNAIL.IGNORED reason=post_removed postId={} key={}",
                    event.getPostId(), event.getThumbnailObjectKey());
            deleteQuietly(event.getThumbnailObjectKey());
            return;
        }

        Optional<PostMediaEntity> media = findMedia(event);
        if (media.isEmpty()) {
            log.warn("THUMBNAIL.REJECTED reason=no_matching_video postId={} mediaId={} key={}",
                    event.getPostId(), event.getMediaId(), event.getThumbnailObjectKey());
            return;
        }

        PostMediaEntity item = media.get();
        String expectedKey = VideoThumbnailRequestedEvent.thumbnailObjectKeyFor(item.getMediaKey());
        if (item.getMediaType() != MediaType.VIDEO || !expectedKey.equals(event.getThumbnailObjectKey())) {
            log.warn("THUMBNAIL.REJECTED reason=unexpected_key postId={} key={} expected={}",
                    event.getPostId(), event.getThumbnailObjectKey(), expectedKey);
            return;
        }

        item.setThumbnailKey(expectedKey);
        postMediaRepository.save(item);
        log.info("THUMBNAIL.SAVED postId={} mediaId={} key={}", event.getPostId(), item.getId(), expectedKey);
    }

    // a worker that predates mediaId does not echo it: fall back to the video whose derived thumbnail key matches
    private Optional<PostMediaEntity> findMedia(VideoThumbnailGeneratedEvent event) {
        if (event.getMediaId() != null) {
            return postMediaRepository.findById(event.getMediaId())
                    .filter(item -> item.getPostId().equals(event.getPostId()));
        }
        return postMediaRepository.findByPostIdOrderByPositionAsc(event.getPostId()).stream()
                .filter(item -> VideoThumbnailRequestedEvent.thumbnailObjectKeyFor(item.getMediaKey())
                        .equals(event.getThumbnailObjectKey()))
                .findFirst();
    }

    private void deleteQuietly(String objectKey) {
        try {
            fileStorageService.deleteObject(objectKey);
        } catch (Exception e) {
            log.warn("THUMBNAIL.CLEANUP.FAILED key={} reason={}", objectKey, e.getMessage());
        }
    }
}
