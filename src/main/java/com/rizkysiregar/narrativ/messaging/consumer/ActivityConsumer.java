package com.rizkysiregar.narrativ.messaging.consumer;

import com.rizkysiregar.narrativ.config.RabbitMQConfig;
import com.rizkysiregar.narrativ.constant.Constants;
import com.rizkysiregar.narrativ.messaging.event.ActivityEvent;
import com.rizkysiregar.narrativ.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.narrativ.model.entity.PostEntity;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.UserStatus;
import com.rizkysiregar.narrativ.repository.FollowRepository;
import com.rizkysiregar.narrativ.repository.PostCrudRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.NotificationInboxService;
import com.rizkysiregar.narrativ.service.firebase.FirebaseNotificationService;
import com.rizkysiregar.narrativ.service.firebase.PushTextResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Tells a user that somebody followed them or pinned their comment: a row in their inbox, then a push. The action is
 * checked again first, so a follow undone (or a pin moved) before the event arrived tells nobody. A user who unfollows
 * and follows again is pushed at most once per {@code app.messaging.like-dedup-ttl-seconds}, like a like.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ActivityConsumer {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PostCrudRepository postCrudRepository;
    private final NotificationInboxService inboxService;
    private final FirebaseNotificationService notificationService;
    private final PushTextResolver textResolver;
    private final EventDeduplicator deduplicator;

    @Value("${app.messaging.like-dedup-ttl-seconds:86400}")
    private long followDedupTtlSeconds;

    @RabbitListener(queues = RabbitMQConfig.ACTIVITY_QUEUE)
    public void handleActivity(ActivityEvent event) {
        if (event.getType() == null || event.getRecipientId() == null || event.getActorId() == null
                || event.getRecipientId().equals(event.getActorId())) {
            log.warn("ACTIVITY.SKIPPED reason=missing_or_self type={}", event.getType());
            return;
        }
        UserEntity recipient = userRepository.findById(event.getRecipientId()).orElse(null);
        if (recipient == null || recipient.isDeleted() || recipient.getStatus() != UserStatus.ACTIVE) {
            log.debug("ACTIVITY.SKIPPED reason=recipient_unavailable recipientId={}", event.getRecipientId());
            return;
        }

        switch (event.getType()) {
            case FOLLOWED -> handleFollow(event);
            case COMMENT_PINNED -> handlePin(event);
            default -> log.warn("ACTIVITY.SKIPPED reason=unsupported_type type={}", event.getType());
        }
    }

    private void handleFollow(ActivityEvent event) {
        if (!followRepository.existsActiveFollow(event.getActorId(), event.getRecipientId())) {
            log.debug("ACTIVITY.SKIPPED reason=follow_undone actorId={}", event.getActorId());
            return;
        }
        inboxService.recordFollow(event.getRecipientId(), event.getActorId());
        push(event, "follow:" + event.getActorId() + ":" + event.getRecipientId(), Duration.ofSeconds(followDedupTtlSeconds),
                "followed", Map.of(
                        "type", Constants.NOTIFICATION_TYPE_FOLLOWED,
                        "actorId", event.getActorId().toString(),
                        "actorUsername", String.valueOf(event.getActorUsername())));
    }

    private void handlePin(ActivityEvent event) {
        if (event.getPostId() == null || event.getCommentId() == null) {
            log.warn("ACTIVITY.SKIPPED reason=missing_ids type=COMMENT_PINNED");
            return;
        }
        boolean stillPinned = postCrudRepository.findById(event.getPostId())
                .map(PostEntity::getPinnedCommentId)
                .filter(pinned -> Objects.equals(pinned, event.getCommentId()))
                .isPresent();
        if (!stillPinned) {
            log.debug("ACTIVITY.SKIPPED reason=pin_moved commentId={}", event.getCommentId());
            return;
        }
        inboxService.recordPin(event.getRecipientId(), event.getActorId(), event.getPostId(), event.getCommentId(),
                event.getPreview());
        push(event, "pin:" + event.getCommentId(), null, "commentPinned", Map.of(
                "type", Constants.NOTIFICATION_TYPE_COMMENT_PINNED,
                "postId", event.getPostId().toString(),
                "commentId", event.getCommentId().toString(),
                "actorId", event.getActorId().toString(),
                "actorUsername", String.valueOf(event.getActorUsername())));
    }

    private void push(ActivityEvent event, String eventKey, Duration ttl, String textKey, Map<String, String> data) {
        boolean reserved = ttl == null
                ? deduplicator.tryReserve(RabbitMQConfig.ACTIVITY_CONSUMER, eventKey)
                : deduplicator.tryReserve(RabbitMQConfig.ACTIVITY_CONSUMER, eventKey, ttl);
        if (!reserved) {
            log.debug("ACTIVITY.PUSH.SKIPPED reason=already_notified key={}", eventKey);
            return;
        }
        try {
            notificationService.sendNotificationToUserDevices(event.getRecipientId(),
                    locale -> textResolver.resolve(locale, textKey, event.getActorUsername(),
                            String.valueOf(event.getPreview())),
                    data);
            log.info("ACTIVITY.PUSHED type={} recipientId={}", event.getType(), event.getRecipientId());
        } catch (RuntimeException e) {
            log.error("ACTIVITY.PUSH.FAILED type={} recipientId={} reason={}", event.getType(),
                    event.getRecipientId(), e.getMessage());
            deduplicator.release(RabbitMQConfig.ACTIVITY_CONSUMER, eventKey);
            throw e;
        }
    }
}
