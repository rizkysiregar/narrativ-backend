package com.rizkysiregar.feedzmore.messaging.consumer;

import com.rizkysiregar.feedzmore.config.RabbitMQConfig;
import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import com.rizkysiregar.feedzmore.service.email.IEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailConsumer {

    private final IEmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    public void handlePostCreated(PostCreatedEvent event) {
        log.info("RECEIVED EVENT FOR EMAIL {}", event);
        try {
            emailService.sendEmailTemplate(
                    event.getEmail(),
                    "Your post status",
                    "post-status",
                    event.getEmailProps()
            );
        } catch (Exception e) {
            log.error("EMAIL FAILED {} ", e.getMessage());
        }
    }
}
