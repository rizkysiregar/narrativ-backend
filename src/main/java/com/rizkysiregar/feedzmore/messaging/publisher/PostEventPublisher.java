package com.rizkysiregar.feedzmore.messaging.publisher;

import com.rizkysiregar.feedzmore.config.RabbitMQConfig;
import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public void publishPostCreatedEvent(PostCreatedEvent event) {
        log.info("PUBLISHING EVENT {} ", event);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.POST_EXCHANGE,
                RabbitMQConfig.POST_CREATED_ROUTING_KEY,
                event
        );
    }
}
