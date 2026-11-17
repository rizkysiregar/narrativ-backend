package com.rizkysiregar.narrativ.messaging.consumer;

import com.rizkysiregar.narrativ.config.RabbitMQConfig;
import com.rizkysiregar.narrativ.constant.Constants;
import com.rizkysiregar.narrativ.messaging.event.CommentRepliedEvent;
import com.rizkysiregar.narrativ.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.UserStatus;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.UserDeviceService;
import com.rizkysiregar.narrativ.service.email.IEmailService;
import com.rizkysiregar.narrativ.service.firebase.FirebaseNotificationService;
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
            log.warn("COMMENT.REPLY.SKIPPED reason=missing_ids commentId={} recipientId={}", event.getCommentId(), event.getRecipientId());
            return;
        }
        if (event.getRecipientId().equals(event.getReplierId())) {
            return;
        }
        String eventKey = event.getCommentId().toString();
        if (!deduplicator.tryReserve(RabbitMQConfig.COMMENT_REPLY_CONSUMER, eventKey)) {
            log.debug("COMMENT.REPLY.SKIPPED reason=already_processed commentId={}", event.getCommentId());
            return;
        }
        try {
            notifyRecipient(event);
        } catch (RuntimeException e) {
            log.error("COMMENT.REPLY.FAILED commentId={} reason={}", event.getCommentId(), e.getMessage());
            deduplicator.release(RabbitMQConfig.COMMENT_REPLY_CONSUMER, eventKey);
            throw e;
        }
    }

    private void notifyRecipient(CommentRepliedEvent event) {
        UserEntity recipient = userRepository.findById(event.getRecipientId()).orElse(null);
        if (recipient == null || recipient.isDeleted() || recipient.getStatus() != UserStatus.ACTIVE) {
            log.debug("COMMENT.REPLY.SKIPPED reason=recipient_unavailable recipientId={}", event.getRecipientId());
            return;
        }

        if (!userDeviceService.getActiveDevice(recipient.getId()).isEmpty()) {
            notificationService.sendNotificationWithUserId(
                    recipient.getId(),
                    Constants.NOTIFICATION_COMMENT_REPLY_TITLE,
                    String.format(Constants.NOTIFICATION_COMMENT_REPLY_BODY,
                            event.getReplierUsername(), event.getContentPreview()));
            log.info("COMMENT.REPLY.PUSHED commentId={} recipientId={}", event.getCommentId(), recipient.getId());
            return;
        }

        emailService.sendEmailTemplate(
                recipient.getEmail(),
                event.getReplierUsername() + " membalas komentar Anda",
                "comment-reply",
                Map.of("username", recipient.getUsername(),
                        "replierUsername", event.getReplierUsername(),
                        "commentPreview", event.getContentPreview()));
        log.info("COMMENT.REPLY.EMAILED commentId={} recipientId={}", event.getCommentId(), recipient.getId());
    }
}
