package com.rizkysiregar.narrativ.messaging.publisher;

import com.rizkysiregar.narrativ.config.RabbitMQConfig;
import com.rizkysiregar.narrativ.messaging.event.PostCreatedEvent;
import com.rizkysiregar.narrativ.messaging.event.PostLikedEvent;
import com.rizkysiregar.narrativ.messaging.event.VideoThumbnailRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PostEventPublisher publisher;

    @Test
    void publishesWithRoutingKeyAndCorrelationId() {
        UUID postId = UUID.randomUUID();
        PostCreatedEvent event = PostCreatedEvent.builder().postId(postId).build();

        publisher.publishPostCreatedEvent(event);

        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.POST_EXCHANGE),
                eq(RabbitMQConfig.POST_CREATED_ROUTING_KEY),
                eq(event),
                correlation.capture());
        assertEquals(postId.toString(), correlation.getValue().getId());
    }

    @Test
    void doesNotPropagatePublishFailure() {
        UUID postId = UUID.randomUUID();
        PostCreatedEvent event = PostCreatedEvent.builder().postId(postId).build();
        org.mockito.Mockito.doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class),
                        any(CorrelationData.class));

        publisher.publishPostCreatedEvent(event);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.POST_EXCHANGE),
                eq(RabbitMQConfig.POST_CREATED_ROUTING_KEY),
                eq(event),
                any(CorrelationData.class));
    }

    @Test
    void publishesThumbnailRequestWithItsOwnRoutingKey() {
        UUID postId = UUID.randomUUID();
        VideoThumbnailRequestedEvent event =
                VideoThumbnailRequestedEvent.of(postId, UUID.randomUUID(), "user/POST_FEED/video.mp4");

        publisher.publishVideoThumbnailRequested(event);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.POST_EXCHANGE),
                eq(RabbitMQConfig.VIDEO_THUMBNAIL_REQUESTED_ROUTING_KEY),
                eq(event),
                any(CorrelationData.class));
        assertEquals("user/POST_FEED/video_thumb.jpg", event.getThumbnailObjectKey());
    }

    @Test
    void publishesPostLikedWithItsOwnRoutingKey() {
        PostLikedEvent event = PostLikedEvent.builder().postId(UUID.randomUUID()).build();

        publisher.publishPostLiked(event);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.POST_EXCHANGE),
                eq(RabbitMQConfig.POST_LIKED_ROUTING_KEY),
                eq(event),
                any(CorrelationData.class));
    }
}
