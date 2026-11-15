package com.rizkysiregar.feedzmore.messaging.consumer;

import com.rizkysiregar.feedzmore.config.RabbitMQConfig;
import com.rizkysiregar.feedzmore.constant.Constants;
import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import com.rizkysiregar.feedzmore.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.feedzmore.repository.FollowRepository;
import com.rizkysiregar.feedzmore.service.firebase.FirebaseNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationConsumer {

    private static final int FOLLOWER_PAGE_SIZE = 500;

    private final FirebaseNotificationService notificationService;
    private final FollowRepository followRepository;
    private final EventDeduplicator deduplicator;

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handlePostCreated(PostCreatedEvent event) {
        UUID authorId = event.getAuthorId();
        if (authorId == null) {
            log.warn("NOTIFICATION.SKIPPED reason=missing_author postId={}", event.getPostId());
            return;
        }

        try {
            String body = String.format(Constants.NOTIFICATION_NEW_FEED_BODY, event.getUsername());
            PageRequest page = PageRequest.of(0, FOLLOWER_PAGE_SIZE);
            List<UUID> followerIds = followRepository.findActiveFollowerIdsByFollowedId(authorId, page);
            long sentCount = 0;
            RuntimeException firstFailure = null;
            while (!followerIds.isEmpty()) {
                for (UUID followerId : followerIds) {
                    // Idempotency is per follower: a redelivery after a partial
                    // failure must not notify followers that already received it.
                    String eventKey = eventKey(event, followerId);
                    if (eventKey != null
                            && !deduplicator.tryReserve(RabbitMQConfig.NOTIFICATION_CONSUMER, eventKey)) {
                        continue;
                    }
                    try {
                        notificationService.sendNotificationWithUserId(
                                followerId, Constants.NOTIFICATION_NEW_FEED_TITLE, body);
                        sentCount++;
                    } catch (RuntimeException e) {
                        log.error("NOTIFICATION.FAILED postId={} followerId={} reason={}",
                                event.getPostId(), followerId, e.getMessage());
                        if (eventKey != null) {
                            deduplicator.release(RabbitMQConfig.NOTIFICATION_CONSUMER, eventKey);
                        }
                        if (firstFailure == null) {
                            firstFailure = e;
                        }
                    }
                }
                if (followerIds.size() < FOLLOWER_PAGE_SIZE) {
                    break;
                }
                UUID afterFollowerId = followerIds.getLast();
                followerIds = followRepository.findActiveFollowerIdsByFollowedIdAfter(authorId, afterFollowerId, page);
            }
            log.info("NOTIFICATION.DISPATCHED postId={} followers={}", event.getPostId(), sentCount);
            if (firstFailure != null) {
                throw firstFailure;
            }
        } catch (RuntimeException e) {
            log.error("NOTIFICATION.FAILED postId={} reason={}", event.getPostId(), e.getMessage());
            throw e;
        }
    }

    private String eventKey(PostCreatedEvent event, UUID followerId) {
        return event.getPostId() != null
                ? event.getPostId() + ":" + followerId
                : null;
    }
}
