package com.rizkysiregar.narrativ.messaging.consumer;

import com.rizkysiregar.narrativ.config.RabbitMQConfig;
import com.rizkysiregar.narrativ.constant.Constants;
import com.rizkysiregar.narrativ.messaging.event.PostCreatedEvent;
import com.rizkysiregar.narrativ.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.narrativ.repository.FollowRepository;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import com.rizkysiregar.narrativ.service.firebase.FirebaseNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationConsumer {

    private static final int FOLLOWER_PAGE_SIZE = 500;

    private final FirebaseNotificationService notificationService;
    private final FollowRepository followRepository;
    private final UserBlockRepository userBlockRepository;
    private final MessageSource messageSource;
    private final EventDeduplicator deduplicator;

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handlePostCreated(PostCreatedEvent event) {
        UUID authorId = event.getAuthorId();
        if (authorId == null) {
            log.warn("NOTIFICATION.SKIPPED reason=missing_author postId={}", event.getPostId());
            return;
        }

        try {
            Map<String, String> data = Map.of(
                    "type", Constants.NOTIFICATION_TYPE_NEW_POST,
                    "postId", String.valueOf(event.getPostId()),
                    "authorId", authorId.toString(),
                    "authorUsername", String.valueOf(event.getUsername()));
            // a follower who blocked the author (or was blocked by them) must not be pushed
            Set<UUID> blocked = new HashSet<>(userBlockRepository.findRelatedUserIds(authorId));
            PageRequest page = PageRequest.of(0, FOLLOWER_PAGE_SIZE);
            List<UUID> followerIds = followRepository.findActiveFollowerIdsByFollowedId(authorId, page);
            long sentCount = 0;
            RuntimeException firstFailure = null;
            while (!followerIds.isEmpty()) {
                for (UUID followerId : followerIds) {
                    if (blocked.contains(followerId)) {
                        continue;
                    }
                    // Idempotency is per follower: a redelivery after a partial
                    // failure must not notify followers that already received it.
                    String eventKey = eventKey(event, followerId);
                    if (eventKey != null
                            && !deduplicator.tryReserve(RabbitMQConfig.NOTIFICATION_CONSUMER, eventKey)) {
                        continue;
                    }
                    try {
                        notificationService.sendNotificationToUserDevices(
                                followerId, locale -> textFor(locale, event.getUsername()), data);
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

    private FirebaseNotificationService.PushText textFor(Locale locale, String authorUsername) {
        // anything but Indonesian gets the English text, same rule as the API
        Locale supported = "id".equals(locale.getLanguage()) ? locale : Locale.ENGLISH;
        return new FirebaseNotificationService.PushText(
                messageSource.getMessage("notification.newPost.title", null, supported),
                messageSource.getMessage("notification.newPost.body", new Object[]{authorUsername}, supported));
    }

    private String eventKey(PostCreatedEvent event, UUID followerId) {
        return event.getPostId() != null
                ? event.getPostId() + ":" + followerId
                : null;
    }
}
