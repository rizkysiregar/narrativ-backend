package com.rizkysiregar.feedzmore.messaging.publisher;

import com.rizkysiregar.feedzmore.config.RabbitMQConfig;
import com.rizkysiregar.feedzmore.messaging.event.CommentRepliedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommentEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    /**
     * Fire-and-forget like {@link PostEventPublisher}: the reply is already saved, so a slow or
     * unreachable broker must neither block the request nor turn it into an error.
     */
    @Async("postEventExecutor")
    public void publishCommentReplied(CommentRepliedEvent event) {
        String eventId = event.getCommentId().toString();
        log.debug("EVENT.PUBLISH type=CommentReplied id={} commentId={}", eventId, event.getCommentId());
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.POST_EXCHANGE,
                    RabbitMQConfig.COMMENT_REPLIED_ROUTING_KEY,
                    event,
                    new CorrelationData(eventId)
            );
        } catch (Exception e) {
            log.error("EVENT.PUBLISH.FAILED type=CommentReplied id={} commentId={} reason={}",
                    eventId, event.getCommentId(), e.getMessage());
        }
    }
}
