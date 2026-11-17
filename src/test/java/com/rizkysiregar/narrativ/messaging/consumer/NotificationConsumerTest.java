package com.rizkysiregar.narrativ.messaging.consumer;

import com.rizkysiregar.narrativ.messaging.event.PostCreatedEvent;
import com.rizkysiregar.narrativ.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.narrativ.repository.FollowRepository;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import com.rizkysiregar.narrativ.service.firebase.FirebaseNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private FirebaseNotificationService notificationService;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private MessageSource messageSource;
    @Mock
    private EventDeduplicator deduplicator;

    @InjectMocks
    private NotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(deduplicator.tryReserve(anyString(), anyString())).thenReturn(true);
        lenient().when(userBlockRepository.findRelatedUserIds(any())).thenReturn(List.of());
    }

    @Test
    void sendsOnlyToActiveFollowersAcrossKeysetPages() {
        UUID authorId = UUID.randomUUID();
        List<UUID> firstPage = IntStream.rangeClosed(1, 500)
                .mapToObj(i -> new UUID(0, i)).toList();
        UUID lastFollower = firstPage.getLast();
        List<UUID> secondPage = List.of(new UUID(0, 501), new UUID(0, 502));
        PageRequest page = PageRequest.of(0, 500);
        PostCreatedEvent event = PostCreatedEvent.builder()
                .postId(UUID.randomUUID())
                .authorId(authorId)
                .username("author")
                .build();
        when(followRepository.findActiveFollowerIdsByFollowedId(authorId, page)).thenReturn(firstPage);
        when(followRepository.findActiveFollowerIdsByFollowedIdAfter(authorId, lastFollower, page))
                .thenReturn(secondPage);

        consumer.handlePostCreated(event);

        for (UUID followerId : firstPage) {
            verify(notificationService).sendNotificationToUserDevices(eq(followerId), any(), any());
        }
        for (UUID followerId : secondPage) {
            verify(notificationService).sendNotificationToUserDevices(eq(followerId), any(), any());
        }
        verifyNoMoreInteractions(notificationService);
        InOrder order = inOrder(followRepository, notificationService);
        order.verify(followRepository).findActiveFollowerIdsByFollowedId(authorId, page);
        order.verify(notificationService).sendNotificationToUserDevices(eq(lastFollower), any(), any());
        order.verify(followRepository).findActiveFollowerIdsByFollowedIdAfter(authorId, lastFollower, page);
        order.verify(notificationService).sendNotificationToUserDevices(eq(secondPage.getFirst()), any(), any());
        verifyNoMoreInteractions(followRepository);
    }

    @Test
    void doesNotBroadcastWhenThereAreNoFollowers() {
        UUID authorId = UUID.randomUUID();
        when(followRepository.findActiveFollowerIdsByFollowedId(authorId, PageRequest.of(0, 500)))
                .thenReturn(List.of());

        consumer.handlePostCreated(PostCreatedEvent.builder().postId(UUID.randomUUID()).authorId(authorId).build());

        verifyNoInteractions(notificationService);
        verify(followRepository).findActiveFollowerIdsByFollowedId(authorId, PageRequest.of(0, 500));
        verifyNoMoreInteractions(followRepository);
    }

    @Test
    void propagatesLookupFailureForRetry() {
        UUID authorId = UUID.randomUUID();
        IllegalStateException failure = new IllegalStateException("lookup failed");
        when(followRepository.findActiveFollowerIdsByFollowedId(authorId, PageRequest.of(0, 500)))
                .thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> consumer.handlePostCreated(PostCreatedEvent.builder().postId(UUID.randomUUID()).authorId(authorId).build()));

        assertSame(failure, thrown);
        verifyNoInteractions(notificationService);
    }

    @Test
    void propagatesNotificationFailureForRetry() {
        UUID authorId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        IllegalStateException failure = new IllegalStateException("notification failed");
        when(followRepository.findActiveFollowerIdsByFollowedId(authorId, PageRequest.of(0, 500)))
                .thenReturn(List.of(followerId));
        doThrow(failure).when(notificationService).sendNotificationToUserDevices(
                eq(followerId), any(), any());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> consumer.handlePostCreated(PostCreatedEvent.builder()
                        .postId(UUID.randomUUID()).authorId(authorId).username("author").build()));

        assertSame(failure, thrown);
    }

    @Test
    void skipsLegacyEventsWithoutAuthorId() {
        consumer.handlePostCreated(PostCreatedEvent.builder().postId(UUID.randomUUID()).build());

        verifyNoInteractions(followRepository, notificationService);
    }

    @Test
    void skipsFollowerThatWasAlreadyNotifiedOnRedelivery() {
        UUID authorId = UUID.randomUUID();
        UUID alreadyNotified = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        PostCreatedEvent event = PostCreatedEvent.builder()
                .postId(UUID.randomUUID())
                .authorId(authorId)
                .username("author")
                .build();
        when(followRepository.findActiveFollowerIdsByFollowedId(authorId, PageRequest.of(0, 500)))
                .thenReturn(List.of(alreadyNotified, pending));
        when(deduplicator.tryReserve(anyString(), anyString())).thenReturn(false, true);

        consumer.handlePostCreated(event);

        verify(notificationService, org.mockito.Mockito.never())
                .sendNotificationToUserDevices(eq(alreadyNotified), any(), any());
        verify(notificationService)
                .sendNotificationToUserDevices(eq(pending), any(), any());
    }

    @Test
    void skipsFollowersWhoBlockedOrWereBlockedByTheAuthor() {
        UUID authorId = UUID.randomUUID();
        UUID blockedFollower = UUID.randomUUID();
        UUID normalFollower = UUID.randomUUID();
        when(followRepository.findActiveFollowerIdsByFollowedId(authorId, PageRequest.of(0, 500)))
                .thenReturn(List.of(blockedFollower, normalFollower));
        when(userBlockRepository.findRelatedUserIds(authorId)).thenReturn(List.of(blockedFollower));

        consumer.handlePostCreated(PostCreatedEvent.builder()
                .postId(UUID.randomUUID()).authorId(authorId).username("author").build());

        verify(notificationService, org.mockito.Mockito.never())
                .sendNotificationToUserDevices(eq(blockedFollower), any(), any());
        verify(notificationService).sendNotificationToUserDevices(eq(normalFollower), any(), any());
    }

    @Test
    void sendsPostDataSoTheAppCanOpenThePost() {
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        when(followRepository.findActiveFollowerIdsByFollowedId(authorId, PageRequest.of(0, 500)))
                .thenReturn(List.of(followerId));

        consumer.handlePostCreated(PostCreatedEvent.builder()
                .postId(postId).authorId(authorId).username("author").build());

        verify(notificationService).sendNotificationToUserDevices(eq(followerId), any(), eq(Map.of(
                "type", "NEW_POST",
                "postId", postId.toString(),
                "authorId", authorId.toString(),
                "authorUsername", "author")));
    }

    @Test
    void localizesTextByDeviceLanguageAndFallsBackToEnglish() {
        UUID authorId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        when(followRepository.findActiveFollowerIdsByFollowedId(authorId, PageRequest.of(0, 500)))
                .thenReturn(List.of(followerId));
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenAnswer(invocation -> {
            String suffix = invocation.getArgument(0).toString().endsWith("title") ? "title" : "body";
            return suffix + "-" + ((Locale) invocation.getArgument(2)).getLanguage();
        });

        consumer.handlePostCreated(PostCreatedEvent.builder()
                .postId(UUID.randomUUID()).authorId(authorId).username("author").build());

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.function.Function<Locale, FirebaseNotificationService.PushText>> captor =
                org.mockito.ArgumentCaptor.forClass(java.util.function.Function.class);
        verify(notificationService).sendNotificationToUserDevices(eq(followerId), captor.capture(), any());
        assertEquals(new FirebaseNotificationService.PushText("title-id", "body-id"),
                captor.getValue().apply(Locale.forLanguageTag("id-ID")));
        assertEquals(new FirebaseNotificationService.PushText("title-en", "body-en"),
                captor.getValue().apply(Locale.forLanguageTag("fr")));
    }
}
