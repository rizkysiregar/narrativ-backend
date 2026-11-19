package com.rizkysiregar.narrativ.messaging.publisher;

import com.rizkysiregar.narrativ.config.RabbitMQConfig;
import com.rizkysiregar.narrativ.messaging.event.ActivityEvent;
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
public class ActivityEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    /**
     * Fire-and-forget like {@link PostEventPublisher}: the follow or pin is already saved, so a slow or unreachable
     * broker must neither block the request nor turn it into an error.
     */
    @Async("postEventExecutor")
    public void publish(ActivityEvent event) {
        String eventId = UUID.randomUUID().toString();
        log.debug("EVENT.PUBLISH type=Activity/{} id={} recipientId={}", event.getType(), eventId, event.getRecipientId());
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.POST_EXCHANGE,
                    RabbitMQConfig.ACTIVITY_ROUTING_KEY,
                    event,
                    new CorrelationData(eventId)
            );
        } catch (Exception e) {
            log.error("EVENT.PUBLISH.FAILED type=Activity/{} id={} reason={}", event.getType(), eventId, e.getMessage());
        }
    }
}
