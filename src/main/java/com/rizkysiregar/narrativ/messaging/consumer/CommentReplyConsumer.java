package com.rizkysiregar.narrativ.messaging.consumer;

import com.rizkysiregar.narrativ.model.enums.NotificationType;
import com.rizkysiregar.narrativ.service.NotificationInboxService;
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
import com.rizkysiregar.narrativ.service.firebase.PushTextResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells people about a new comment on a post:
 * <ul>
 *     <li>the owner of the comment that was answered gets "replied to your comment": a push when the user has an
 *     active device, an email otherwise;</li>
 *     <li>the owner of the post always gets a push about any comment or reply on their post. When that is also the
 *     owner of the answered comment they get the single "replied to your comment" push, not two.</li>
 * </ul>
 * Nobody is told about their own comment. Each recipient is deduplicated on its own, so a redelivery after a partial
 * failure only retries the recipients that were not reached.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CommentReplyConsumer {

    private enum Kind {
        /** your comment was answered */
        REPLIED(Constants.NOTIFICATION_TYPE_COMMENT_REPLIED, "commentReplied", NotificationType.COMMENT_REPLIED),
        /** someone commented on your post */
        COMMENTED(Constants.NOTIFICATION_TYPE_POST_COMMENTED, "postCommented", NotificationType.POST_COMMENTED);

        private final String type;
        private final String textKey;
        private final NotificationType inboxType;

        Kind(String type, String textKey, NotificationType inboxType) {
            this.type = type;
            this.textKey = textKey;
            this.inboxType = inboxType;
        }
    }

    private final UserRepository userRepository;
    private final UserDeviceService userDeviceService;
    private final FirebaseNotificationService notificationService;
    private final PushTextResolver textResolver;
    private final IEmailService emailService;
    private final EventDeduplicator deduplicator;
    private final NotificationInboxService inboxService;

    @RabbitListener(queues = RabbitMQConfig.COMMENT_REPLY_QUEUE)
    public void handleCommentReplied(CommentRepliedEvent event) {
        if (event.getCommentId() == null) {
            log.warn("COMMENT.REPLY.SKIPPED reason=missing_comment_id postId={}", event.getPostId());
            return;
        }

        UUID actorId = event.getReplierId();
        Map<UUID, Kind> recipients = new LinkedHashMap<>();
        if (event.getRecipientId() != null && !event.getRecipientId().equals(actorId)) {
            recipients.put(event.getRecipientId(), Kind.REPLIED);
        }
        if (event.getPostOwnerId() != null && !event.getPostOwnerId().equals(actorId)) {
            recipients.putIfAbsent(event.getPostOwnerId(), Kind.COMMENTED);
        }

        RuntimeException firstFailure = null;
        for (Map.Entry<UUID, Kind> recipient : recipients.entrySet()) {
            String eventKey = event.getCommentId() + ":" + recipient.getKey();
            if (!deduplicator.tryReserve(RabbitMQConfig.COMMENT_REPLY_CONSUMER, eventKey)) {
                log.debug("COMMENT.REPLY.SKIPPED reason=already_processed commentId={} recipientId={}",
                        event.getCommentId(), recipient.getKey());
                continue;
            }
            try {
                notifyRecipient(event, recipient.getKey(), recipient.getValue());
            } catch (RuntimeException e) {
                log.error("COMMENT.REPLY.FAILED commentId={} recipientId={} reason={}",
                        event.getCommentId(), recipient.getKey(), e.getMessage());
                deduplicator.release(RabbitMQConfig.COMMENT_REPLY_CONSUMER, eventKey);
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private void notifyRecipient(CommentRepliedEvent event, UUID recipientId, Kind kind) {
        UserEntity recipient = userRepository.findById(recipientId).orElse(null);
        if (recipient == null || recipient.isDeleted() || recipient.getStatus() != UserStatus.ACTIVE) {
            log.debug("COMMENT.REPLY.SKIPPED reason=recipient_unavailable recipientId={}", recipientId);
            return;
        }
        // the inbox holds it whether a push, an email or nothing goes out below
        inboxService.recordComment(recipient.getId(), kind.inboxType, event.getReplierId(), event.getPostId(),
                event.getCommentId(), event.getContentPreview());

        if (!userDeviceService.getActiveDevice(recipient.getId()).isEmpty()) {
            Map<String, String> data = Map.of(
                    "type", kind.type,
                    "postId", String.valueOf(event.getPostId()),
                    "commentId", event.getCommentId().toString(),
                    "actorId", String.valueOf(event.getReplierId()),
                    "actorUsername", String.valueOf(event.getReplierUsername()));
            notificationService.sendNotificationToUserDevices(recipient.getId(),
                    locale -> textResolver.resolve(locale, kind.textKey,
                            event.getReplierUsername(), event.getContentPreview()),
                    data);
            log.info("COMMENT.REPLY.PUSHED commentId={} recipientId={} kind={}",
                    event.getCommentId(), recipient.getId(), kind);
            return;
        }

        // the email only exists for replies; a post owner without a device just misses the push
        if (kind != Kind.REPLIED) {
            log.debug("COMMENT.REPLY.SKIPPED reason=no_device commentId={} recipientId={}",
                    event.getCommentId(), recipient.getId());
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
