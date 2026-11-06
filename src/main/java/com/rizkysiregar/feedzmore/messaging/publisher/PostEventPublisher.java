package com.rizkysiregar.feedzmore.messaging.publisher;

import com.rizkysiregar.feedzmore.config.RabbitMQConfig;
import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import com.rizkysiregar.feedzmore.messaging.event.VideoThumbnailRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    /**
     * Published asynchronously so a slow/unreachable broker never blocks the
     * HTTP request that creates the post. The correlation id lets the global
     * publisher-confirm callback report whether the broker accepted the message;
     * unroutable messages are reported by the returns callback. Publish failures
     * are logged instead of propagating to the caller.
     */
    @Async("postEventExecutor")
    public void publishPostCreatedEvent(PostCreatedEvent event) {
        String eventId = resolveEventId(event);
        CorrelationData correlationData = new CorrelationData(eventId);
        log.info("PUBLISHING EVENT id={} {}", eventId, event);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.POST_EXCHANGE,
                    RabbitMQConfig.POST_CREATED_ROUTING_KEY,
                    event,
                    correlationData
            );
        } catch (Exception e) {
            log.error("Failed to publish PostCreatedEvent id={} postId={}: {}",
                    eventId, event.getPostId(), e.getMessage());
        }
    }

    /**
     * Asks the media worker to generate a video thumbnail. Same fire-and-forget
     * semantics as {@link #publishPostCreatedEvent}: the post already exists with a
     * null thumbnail and is updated when the worker's result event arrives.
     */
    @Async("postEventExecutor")
    public void publishVideoThumbnailRequested(VideoThumbnailRequestedEvent event) {
        String eventId = event.getPostId() != null
                ? event.getPostId().toString()
                : UUID.randomUUID().toString();
        CorrelationData correlationData = new CorrelationData(eventId);
        log.info("PUBLISHING EVENT id={} {}", eventId, event);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.POST_EXCHANGE,
                    RabbitMQConfig.VIDEO_THUMBNAIL_REQUESTED_ROUTING_KEY,
                    event,
                    correlationData
            );
        } catch (Exception e) {
            log.error("Failed to publish VideoThumbnailRequestedEvent id={} postId={}: {}",
                    eventId, event.getPostId(), e.getMessage());
        }
    }

    private String resolveEventId(PostCreatedEvent event) {
        return event.getPostId() != null
                ? event.getPostId().toString()
                : UUID.randomUUID().toString();
    }
}
