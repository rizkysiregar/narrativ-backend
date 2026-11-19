package com.rizkysiregar.narrativ.messaging.consumer;

import com.rizkysiregar.narrativ.repository.LikeRepository;
import com.rizkysiregar.narrativ.service.NotificationInboxService;
import static org.mockito.Mockito.lenient;
import com.rizkysiregar.narrativ.config.RabbitMQConfig;
import com.rizkysiregar.narrativ.messaging.event.PostLikedEvent;
import com.rizkysiregar.narrativ.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.UserStatus;
import com.rizkysiregar.narrativ.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostLikeConsumerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FirebaseNotificationService notificationService;
    @Mock
    private PushTextResolver textResolver;
    @Mock
    private EventDeduplicator deduplicator;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private NotificationInboxService inboxService;

    @InjectMocks
    private PostLikeConsumer consumer;

    private final UUID postId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID likerId = UUID.randomUUID();
    private final String key = postId + ":" + likerId;
    private PostLikedEvent event;
    private UserEntity owner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "likeDedupTtlSeconds", 86400L);
        event = PostLikedEvent.builder().postId(postId).postOwnerId(ownerId).likerId(likerId)
                .likerUsername("budi").build();
        owner = new UserEntity();
        owner.setId(ownerId);
        owner.setStatus(UserStatus.ACTIVE);
        lenient().when(likeRepository.existsByUserIdAndPostId(likerId, postId)).thenReturn(true);
    }

    private void reserve(boolean first) {
        when(deduplicator.tryReserve(RabbitMQConfig.POST_LIKE_CONSUMER, key, Duration.ofSeconds(86400)))
                .thenReturn(first);
    }

    @Test
    void pushesToThePostOwnerWithThePostData() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        reserve(true);

        consumer.handlePostLiked(event);

        verify(notificationService).sendNotificationToUserDevices(eq(ownerId), any(), eq(Map.of(
                "type", "POST_LIKED",
                "postId", postId.toString(),
                "actorId", likerId.toString(),
                "actorUsername", "budi")));
    }

    @Test
    void doesNotPushAgainForTheSameLikerAndPost() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        reserve(false);

        consumer.handlePostLiked(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    void doesNotPushForALikeOnYourOwnPost() {
        event.setLikerId(ownerId);

        consumer.handlePostLiked(event);

        verifyNoInteractions(userRepository, deduplicator, notificationService);
    }

    @Test
    void skipsABannedOwnerWithoutUsingUpTheReservation() {
        owner.setStatus(UserStatus.BANNED);
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));

        consumer.handlePostLiked(event);

        verifyNoInteractions(deduplicator, notificationService);
    }

    @Test
    void releasesTheReservationAndRethrowsWhenThePushFails() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        reserve(true);
        doThrow(new IllegalStateException("fcm down")).when(notificationService)
                .sendNotificationToUserDevices(eq(ownerId), any(), any());

        assertThrows(IllegalStateException.class, () -> consumer.handlePostLiked(event));

        verify(deduplicator).release(RabbitMQConfig.POST_LIKE_CONSUMER, key);
    }

    @Test
    void ignoresAnEventWithoutIds() {
        consumer.handlePostLiked(PostLikedEvent.builder().postId(postId).build());

        verify(notificationService, never()).sendNotificationToUserDevices(any(), any(), any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void theInboxKeepsTheLikeEvenWhenThePushWasAlreadySent() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        reserve(false);

        consumer.handlePostLiked(event);

        verify(inboxService).recordLike(ownerId, likerId, postId);
        verifyNoInteractions(notificationService);
    }

    @Test
    void aLikeUndoneBeforeTheEventArrivedTellsNobody() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(likeRepository.existsByUserIdAndPostId(likerId, postId)).thenReturn(false);

        consumer.handlePostLiked(event);

        verifyNoInteractions(inboxService, notificationService, deduplicator);
    }
}
