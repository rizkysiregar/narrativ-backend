package com.rizkysiregar.feedzmore.messaging.consumer;

import com.rizkysiregar.feedzmore.config.RabbitMQConfig;
import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import com.rizkysiregar.feedzmore.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.feedzmore.service.email.IEmailService;
import com.rizkysiregar.feedzmore.i18n.LocalizedMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailConsumer {

    private final IEmailService emailService;
    private final EventDeduplicator deduplicator;

    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    public void handlePostCreated(PostCreatedEvent event) {
        String eventKey = eventKey(event);
        if (eventKey != null
                && !deduplicator.tryReserve(RabbitMQConfig.EMAIL_CONSUMER, eventKey)) {
            log.debug("EMAIL.SKIPPED reason=already_processed postId={}", event.getPostId());
            return;
        }
        log.debug("EMAIL.EVENT.RECEIVED postId={}", event.getPostId());
        try {
            Locale locale = LocalizedMessages.supportedLocale(event.getLocale());
            LocalizedMessages.withLocale(locale, () -> emailService.sendEmailTemplate(
                    event.getEmail(),
                    LocalizedMessages.text("email.post.subject", locale, "Your post status"),
                    "post-status",
                    event.getEmailProps()
            ));
        } catch (Exception e) {
            log.error("EMAIL.FAILED postId={} reason={}", event.getPostId(), e.getMessage());
            if (eventKey != null) {
                deduplicator.release(RabbitMQConfig.EMAIL_CONSUMER, eventKey);
            }
            throw e;
        }
    }

    private String eventKey(PostCreatedEvent event) {
        return event.getPostId() != null ? event.getPostId().toString() : null;
    }
}
