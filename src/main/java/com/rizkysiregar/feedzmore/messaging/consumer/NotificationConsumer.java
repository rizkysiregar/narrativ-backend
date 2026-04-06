package com.rizkysiregar.feedzmore.messaging.consumer;

import com.rizkysiregar.feedzmore.config.RabbitMQConfig;
import com.rizkysiregar.feedzmore.constant.Constants;
import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import com.rizkysiregar.feedzmore.service.firebase.FirebaseNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationConsumer {

    private final FirebaseNotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handlePostCreated(PostCreatedEvent event) {
        log.info("RECEIVED EVENT: {}", event);
        try {
            notificationService.sendNotificationToTopic(
                    Constants.FIREBASE_TOPIC_GLOBAL,
                    Constants.NOTIFICATION_NEW_FEED_TITLE,
                    String.format(Constants.NOTIFICATION_NEW_FEED_BODY, event.getUsername())
            );
            log.info("NOTIFICATION SENT for {}", event.getUsername());
        } catch (Exception e) {
            log.error("NOTIFICATION FAILED {}", e.getMessage());
            throw e;
        }
    }
}
