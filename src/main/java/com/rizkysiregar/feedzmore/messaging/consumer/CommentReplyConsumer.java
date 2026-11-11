package com.rizkysiregar.feedzmore.messaging.consumer;

import com.rizkysiregar.feedzmore.config.RabbitMQConfig;
import com.rizkysiregar.feedzmore.constant.Constants;
import com.rizkysiregar.feedzmore.messaging.event.CommentRepliedEvent;
import com.rizkysiregar.feedzmore.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.UserStatus;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.UserDeviceService;
import com.rizkysiregar.feedzmore.service.email.IEmailService;
import com.rizkysiregar.feedzmore.service.firebase.FirebaseNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tells the owner of a comment that somebody replied to it: a push when the user has an active
 * device, an email otherwise.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommentReplyConsumer {

    private final UserRepository userRepository;
    private final UserDeviceService userDeviceService;
    private final FirebaseNotificationService notificationService;
    private final IEmailService emailService;
    private final EventDeduplicator deduplicator;

    @RabbitListener(queues = RabbitMQConfig.COMMENT_REPLY_QUEUE)
    public void handleCommentReplied(CommentRepliedEvent event) {
        if (event.getCommentId() == null || event.getRecipientId() == null) {
            log.warn("COMMENT REPLY NOTIFICATION SKIPPED: missing commentId or recipientId {}", event);
            return;
        }
        if (event.getRecipientId().equals(event.getReplierId())) {
            return;
        }
        String eventKey = event.getCommentId().toString();
        if (!deduplicator.tryReserve(RabbitMQConfig.COMMENT_REPLY_CONSUMER, eventKey)) {
            log.info("COMMENT REPLY NOTIFICATION SKIPPED (already processed) commentId={}", event.getCommentId());
            return;
        }
        try {
            notifyRecipient(event);
        } catch (RuntimeException e) {
            log.error("COMMENT REPLY NOTIFICATION FAILED commentId={}: {}", event.getCommentId(), e.getMessage());
            deduplicator.release(RabbitMQConfig.COMMENT_REPLY_CONSUMER, eventKey);
            throw e;
        }
    }

    private void notifyRecipient(CommentRepliedEvent event) {
        UserEntity recipient = userRepository.findById(event.getRecipientId()).orElse(null);
        if (recipient == null || recipient.isDeleted() || recipient.getStatus() != UserStatus.ACTIVE) {
            log.info("COMMENT REPLY NOTIFICATION SKIPPED: recipient {} is gone or banned", event.getRecipientId());
            return;
        }

        if (!userDeviceService.getActiveDevice(recipient.getId()).isEmpty()) {
            notificationService.sendNotificationWithUserId(
                    recipient.getId(),
                    Constants.NOTIFICATION_COMMENT_REPLY_TITLE,
                    String.format(Constants.NOTIFICATION_COMMENT_REPLY_BODY,
                            event.getReplierUsername(), event.getContentPreview()));
            log.info("COMMENT REPLY PUSHED commentId={} to {}", event.getCommentId(), recipient.getId());
            return;
        }

        emailService.sendEmailTemplate(
                recipient.getEmail(),
                event.getReplierUsername() + " membalas komentar Anda",
                "comment-reply",
                Map.of("username", recipient.getUsername(),
                        "replierUsername", event.getReplierUsername(),
                        "commentPreview", event.getContentPreview()));
        log.info("COMMENT REPLY EMAILED commentId={} to {}", event.getCommentId(), recipient.getId());
    }
}
