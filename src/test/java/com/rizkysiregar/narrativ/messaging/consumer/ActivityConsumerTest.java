package com.rizkysiregar.narrativ.messaging.consumer;

import com.rizkysiregar.narrativ.config.RabbitMQConfig;
import com.rizkysiregar.narrativ.messaging.event.ActivityEvent;
import com.rizkysiregar.narrativ.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.narrativ.model.entity.PostEntity;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.NotificationType;
import com.rizkysiregar.narrativ.model.enums.UserStatus;
import com.rizkysiregar.narrativ.repository.FollowRepository;
import com.rizkysiregar.narrativ.repository.PostCrudRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.NotificationInboxService;
import com.rizkysiregar.narrativ.service.firebase.FirebaseNotificationService;
import com.rizkysiregar.narrativ.service.firebase.PushTextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityConsumerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private PostCrudRepository postCrudRepository;
    @Mock
    private NotificationInboxService inboxService;
    @Mock
    private FirebaseNotificationService notificationService;
    @Mock
    private PushTextResolver textResolver;
    @Mock
    private EventDeduplicator deduplicator;

    @InjectMocks
    private ActivityConsumer consumer;

    private final UUID recipientId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final UUID commentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "followDedupTtlSeconds", 86400L);
    }

    private void activeRecipient() {
        UserEntity recipient = new UserEntity();
        recipient.setId(recipientId);
        recipient.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
    }

    private ActivityEvent follow() {
        return ActivityEvent.builder().type(NotificationType.FOLLOWED).recipientId(recipientId).actorId(actorId)
                .actorUsername("ani").build();
    }

    private ActivityEvent pin() {
        return ActivityEvent.builder().type(NotificationType.COMMENT_PINNED).recipientId(recipientId).actorId(actorId)
                .actorUsername("ani").postId(postId).commentId(commentId).preview("keren").build();
    }

    @Test
    void aFollowGoesToTheInboxAndIsPushed() {
        activeRecipient();
        when(followRepository.existsActiveFollow(actorId, recipientId)).thenReturn(true);
        when(deduplicator.tryReserve(eq(RabbitMQConfig.ACTIVITY_CONSUMER), anyString(), eq(Duration.ofSeconds(86400))))
                .thenReturn(true);

        consumer.handleActivity(follow());

        verify(inboxService).recordFollow(recipientId, actorId);
        verify(notificationService).sendNotificationToUserDevices(eq(recipientId), any(), eq(Map.of(
                "type", "FOLLOWED", "actorId", actorId.toString(), "actorUsername", "ani")));
    }

    @Test
    void aFollowUndoneBeforeTheEventArrivedTellsNobody() {
        activeRecipient();
        when(followRepository.existsActiveFollow(actorId, recipientId)).thenReturn(false);

        consumer.handleActivity(follow());

        verifyNoInteractions(inboxService, notificationService, deduplicator);
    }

    @Test
    void aPinGoesToTheInboxAndIsPushedWithThePost() {
        activeRecipient();
        when(postCrudRepository.findById(postId))
                .thenReturn(Optional.of(PostEntity.builder().id(postId).pinnedCommentId(commentId).build()));
        when(deduplicator.tryReserve(RabbitMQConfig.ACTIVITY_CONSUMER, "pin:" + commentId)).thenReturn(true);

        consumer.handleActivity(pin());

        verify(inboxService).recordPin(recipientId, actorId, postId, commentId, "keren");
        verify(notificationService).sendNotificationToUserDevices(eq(recipientId), any(), eq(Map.of(
                "type", "COMMENT_PINNED", "postId", postId.toString(), "commentId", commentId.toString(),
                "actorId", actorId.toString(), "actorUsername", "ani")));
    }

    @Test
    void aPinMovedBeforeTheEventArrivedTellsNobody() {
        activeRecipient();
        when(postCrudRepository.findById(postId))
                .thenReturn(Optional.of(PostEntity.builder().id(postId).pinnedCommentId(UUID.randomUUID()).build()));

        consumer.handleActivity(pin());

        verifyNoInteractions(inboxService, notificationService);
    }

    @Test
    void aFailedPushReleasesTheKeySoARedeliveryTriesAgain() {
        activeRecipient();
        when(postCrudRepository.findById(postId))
                .thenReturn(Optional.of(PostEntity.builder().id(postId).pinnedCommentId(commentId).build()));
        when(deduplicator.tryReserve(RabbitMQConfig.ACTIVITY_CONSUMER, "pin:" + commentId)).thenReturn(true);
        doThrow(new IllegalStateException("fcm down")).when(notificationService)
                .sendNotificationToUserDevices(eq(recipientId), any(), any());

        assertThrows(IllegalStateException.class, () -> consumer.handleActivity(pin()));

        verify(deduplicator).release(RabbitMQConfig.ACTIVITY_CONSUMER, "pin:" + commentId);
    }

    @Test
    void nobodyIsToldAboutThemselves() {
        consumer.handleActivity(ActivityEvent.builder().type(NotificationType.FOLLOWED)
                .recipientId(actorId).actorId(actorId).build());

        verifyNoInteractions(userRepository, inboxService, notificationService);
    }
}
