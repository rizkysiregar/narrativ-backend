package com.rizkysiregar.narrativ.messaging.consumer;

import com.rizkysiregar.narrativ.config.RabbitMQConfig;
import com.rizkysiregar.narrativ.constant.Constants;
import com.rizkysiregar.narrativ.messaging.event.PostLikedEvent;
import com.rizkysiregar.narrativ.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.UserStatus;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.firebase.FirebaseNotificationService;
import com.rizkysiregar.narrativ.service.firebase.PushTextResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Tells the owner of a post that somebody liked it. Each liker notifies the owner of a post at most once per
 * {@code app.messaging.like-dedup-ttl-seconds}, however many times they unlike and like again.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PostLikeConsumer {

    private final UserRepository userRepository;
    private final FirebaseNotificationService notificationService;
    private final PushTextResolver textResolver;
    private final EventDeduplicator deduplicator;

    @Value("${app.messaging.like-dedup-ttl-seconds:86400}")
    private long likeDedupTtlSeconds;

    @RabbitListener(queues = RabbitMQConfig.POST_LIKED_QUEUE)
    public void handlePostLiked(PostLikedEvent event) {
        if (event.getPostId() == null || event.getPostOwnerId() == null || event.getLikerId() == null) {
            log.warn("POST.LIKE.PUSH.SKIPPED reason=missing_ids postId={}", event.getPostId());
            return;
        }
        // liking your own post tells nobody anything
        if (event.getPostOwnerId().equals(event.getLikerId())) {
            return;
        }
        UserEntity owner = userRepository.findById(event.getPostOwnerId()).orElse(null);
        if (owner == null || owner.isDeleted() || owner.getStatus() != UserStatus.ACTIVE) {
            log.debug("POST.LIKE.PUSH.SKIPPED reason=owner_unavailable ownerId={}", event.getPostOwnerId());
            return;
        }

        String eventKey = event.getPostId() + ":" + event.getLikerId();
        if (!deduplicator.tryReserve(RabbitMQConfig.POST_LIKE_CONSUMER, eventKey, Duration.ofSeconds(likeDedupTtlSeconds))) {
            log.debug("POST.LIKE.PUSH.SKIPPED reason=already_notified postId={} likerId={}",
                    event.getPostId(), event.getLikerId());
            return;
        }
        try {
            Map<String, String> data = Map.of(
                    "type", Constants.NOTIFICATION_TYPE_POST_LIKED,
                    "postId", event.getPostId().toString(),
                    "actorId", event.getLikerId().toString(),
                    "actorUsername", String.valueOf(event.getLikerUsername()));
            notificationService.sendNotificationToUserDevices(owner.getId(),
                    locale -> textResolver.resolve(locale, "postLiked", event.getLikerUsername()), data);
            log.info("POST.LIKE.PUSHED postId={} ownerId={}", event.getPostId(), owner.getId());
        } catch (RuntimeException e) {
            log.error("POST.LIKE.PUSH.FAILED postId={} reason={}", event.getPostId(), e.getMessage());
            deduplicator.release(RabbitMQConfig.POST_LIKE_CONSUMER, eventKey);
            throw e;
        }
    }
}
