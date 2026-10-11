package com.rizkysiregar.feedzmore.messaging.consumer;

import com.rizkysiregar.feedzmore.config.RabbitMQConfig;
import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import com.rizkysiregar.feedzmore.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.feedzmore.model.enums.MediaType;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.service.FileStorageService;
import com.rizkysiregar.feedzmore.service.VideoProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoThumbnailConsumerTest {

    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private VideoProcessingService videoProcessingService;
    @Mock
    private PostCrudRepository postCrudRepository;
    @Mock
    private EventDeduplicator deduplicator;

    @InjectMocks
    private VideoThumbnailConsumer consumer;

    @Test
    void ignoresNonVideoEvents() {
        consumer.handlePostCreated(PostCreatedEvent.builder()
                .postId(UUID.randomUUID())
                .mediaType(MediaType.IMAGE)
                .build());

        verifyNoInteractions(fileStorageService, videoProcessingService, postCrudRepository, deduplicator);
    }

    @Test
    void skipsAlreadyProcessedVideo() {
        UUID postId = UUID.randomUUID();
        when(deduplicator.tryReserve(RabbitMQConfig.VIDEO_THUMBNAIL_CONSUMER, postId.toString()))
                .thenReturn(false);

        consumer.handlePostCreated(PostCreatedEvent.builder()
                .postId(postId)
                .mediaType(MediaType.VIDEO)
                .mediaUrl("user/POST_FEED/video.mp4")
                .build());

        verifyNoInteractions(fileStorageService, videoProcessingService, postCrudRepository);
    }

    @Test
    void propagatesFailureAndReleasesReservation() {
        UUID postId = UUID.randomUUID();
        PostCreatedEvent event = PostCreatedEvent.builder()
                .postId(postId)
                .mediaType(MediaType.VIDEO)
                .mediaUrl("user/POST_FEED/video.mp4")
                .build();
        when(deduplicator.tryReserve(RabbitMQConfig.VIDEO_THUMBNAIL_CONSUMER, postId.toString()))
                .thenReturn(true);
        when(fileStorageService.getObject(anyString(), any()))
                .thenThrow(new IllegalStateException("storage down"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> consumer.handlePostCreated(event));

        verify(deduplicator).release(RabbitMQConfig.VIDEO_THUMBNAIL_CONSUMER, postId.toString());
    }
}
