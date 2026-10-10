package com.rizkysiregar.feedzmore.messaging.publisher;

import com.rizkysiregar.feedzmore.config.RabbitMQConfig;
import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    /**
     * Published asynchronously so a slow/unreachable broker never blocks the
     * HTTP request that creates the post. Publish failures are logged instead of
     * propagating to the caller.
     */
    @Async("postEventExecutor")
    public void publishPostCreatedEvent(PostCreatedEvent event) {
        log.info("PUBLISHING EVENT {} ", event);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.POST_EXCHANGE,
                    RabbitMQConfig.POST_CREATED_ROUTING_KEY,
                    event
            );
        } catch (Exception e) {
            log.error("Failed to publish PostCreatedEvent for post {}: {}", event.getPostId(), e.getMessage());
        }
    }
}
