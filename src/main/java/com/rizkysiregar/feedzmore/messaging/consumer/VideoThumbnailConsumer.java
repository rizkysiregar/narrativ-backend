package com.rizkysiregar.feedzmore.messaging.consumer;

import com.rizkysiregar.feedzmore.config.RabbitMQConfig;
import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import com.rizkysiregar.feedzmore.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.feedzmore.model.dto.ImageObject;
import com.rizkysiregar.feedzmore.model.entity.PostEntity;
import com.rizkysiregar.feedzmore.model.enums.MediaType;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.service.FileStorageService;
import com.rizkysiregar.feedzmore.service.VideoProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Legacy in-process thumbnail generation (needs ffmpeg in this image). Disabled once
 * {@code app.thumbnail.worker-enabled=true}, when the media worker takes over.
 */
@Component
@ConditionalOnProperty(name = "app.thumbnail.worker-enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class VideoThumbnailConsumer {

    private final FileStorageService fileStorageService;
    private final VideoProcessingService videoProcessingService;
    private final PostCrudRepository postCrudRepository;
    private final EventDeduplicator deduplicator;

    @Value("${minio.bucket}")
    private String bucketName;

    @RabbitListener(queues = RabbitMQConfig.VIDEO_THUMBNAIL_QUEUE)
    public void handlePostCreated(PostCreatedEvent event) {
        if (event.getMediaType() != MediaType.VIDEO) {
            return;
        }
        String eventKey = event.getPostId() != null ? event.getPostId().toString() : null;
        if (eventKey != null
                && !deduplicator.tryReserve(RabbitMQConfig.VIDEO_THUMBNAIL_CONSUMER, eventKey)) {
            log.info("THUMBNAIL SKIPPED (already processed) postId={}", event.getPostId());
            return;
        }
        log.info("GENERATING THUMBNAIL for postId={}", event.getPostId());
        Path videoPath = null;
        Path thumbPath = null;
        try {
            ImageObject videoObject = fileStorageService.getObject(event.getMediaUrl(), bucketName);
            videoPath = Files.createTempFile("feedzmore-thumb-video-", ".mp4");
            try (InputStream in = videoObject.getStream()) {
                Files.copy(in, videoPath, StandardCopyOption.REPLACE_EXISTING);
            }

            thumbPath = Files.createTempFile("feedzmore-thumb-", ".jpg");
            videoProcessingService.extractThumbnail(videoPath, thumbPath);

            String thumbnailObjectName = buildThumbnailObjectName(event.getMediaUrl());
            try (FileInputStream fis = new FileInputStream(thumbPath.toFile())) {
                fileStorageService.uploadObject(thumbnailObjectName, fis, Files.size(thumbPath), "image/jpeg");
            }

            PostEntity post = postCrudRepository.findById(event.getPostId()).orElse(null);
            if (post != null) {
                post.setThumbnailUrl(thumbnailObjectName);
                postCrudRepository.save(post);
                log.info("THUMBNAIL SAVED for postId={} -> {}", event.getPostId(), thumbnailObjectName);
            }
        } catch (Exception e) {
            log.error("THUMBNAIL FAILED for postId={}: {}", event.getPostId(), e.getMessage());
            if (eventKey != null) {
                deduplicator.release(RabbitMQConfig.VIDEO_THUMBNAIL_CONSUMER, eventKey);
            }
            throw new RuntimeException(e);
        } finally {
            try {
                if (videoPath != null) Files.deleteIfExists(videoPath);
                if (thumbPath != null) Files.deleteIfExists(thumbPath);
            } catch (Exception ignored) {
            }
        }
    }

    private String buildThumbnailObjectName(String mediaUrl) {
        int dot = mediaUrl.lastIndexOf('.');
        if (dot == -1) {
            return mediaUrl + "_thumb.jpg";
        }
        return mediaUrl.substring(0, dot) + "_thumb.jpg";
    }
}
