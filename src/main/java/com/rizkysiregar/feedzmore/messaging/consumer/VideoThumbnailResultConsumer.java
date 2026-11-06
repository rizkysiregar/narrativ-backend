package com.rizkysiregar.feedzmore.messaging.consumer;

import com.rizkysiregar.feedzmore.config.RabbitMQConfig;
import com.rizkysiregar.feedzmore.messaging.event.VideoThumbnailGeneratedEvent;
import com.rizkysiregar.feedzmore.messaging.event.VideoThumbnailRequestedEvent;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Stores the thumbnail produced by the media worker on the post. The update is
 * idempotent (same post, same key), so redelivery needs no deduplication.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VideoThumbnailResultConsumer {

    private final PostCrudRepository postCrudRepository;
    private final FileStorageService fileStorageService;

    @RabbitListener(queues = RabbitMQConfig.VIDEO_THUMBNAIL_RESULT_QUEUE)
    public void handleThumbnailGenerated(VideoThumbnailGeneratedEvent event) {
        if (event.getPostId() == null || event.getThumbnailObjectKey() == null) {
            log.warn("THUMBNAIL RESULT IGNORED: missing postId or thumbnailObjectKey {}", event);
            return;
        }

        Optional<PostEntity> found = postCrudRepository.findById(event.getPostId());
        if (found.isEmpty() || found.get().isDeleted()) {
            // The post was removed while the worker was busy: don't leave an orphan object behind.
            log.info("THUMBNAIL RESULT FOR REMOVED POST postId={}, deleting {}",
                    event.getPostId(), event.getThumbnailObjectKey());
            deleteQuietly(event.getThumbnailObjectKey());
            return;
        }

        PostEntity post = found.get();
        String expectedKey = VideoThumbnailRequestedEvent.thumbnailObjectKeyFor(post.getMediaUrl());
        if (!expectedKey.equals(event.getThumbnailObjectKey())) {
            log.warn("THUMBNAIL RESULT REJECTED postId={}: unexpected key {} (expected {})",
                    event.getPostId(), event.getThumbnailObjectKey(), expectedKey);
            return;
        }

        post.setThumbnailUrl(expectedKey);
        postCrudRepository.save(post);
        log.info("THUMBNAIL SAVED for postId={} -> {}", event.getPostId(), expectedKey);
    }

    private void deleteQuietly(String objectKey) {
        try {
            fileStorageService.deleteObject(objectKey);
        } catch (Exception e) {
            log.warn("THUMBNAIL ORPHAN CLEANUP FAILED for {}: {}", objectKey, e.getMessage());
        }
    }
}
