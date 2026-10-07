package com.rizkysiregar.feedzmore.messaging.consumer;

import com.rizkysiregar.feedzmore.constant.Constants;
import com.rizkysiregar.feedzmore.messaging.event.PostCreatedEvent;
import com.rizkysiregar.feedzmore.repository.FollowRepository;
import com.rizkysiregar.feedzmore.service.firebase.FirebaseNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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

    @InjectMocks
    private NotificationConsumer consumer;

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

        String body = String.format(Constants.NOTIFICATION_NEW_FEED_BODY, "author");
        for (UUID followerId : firstPage) {
            verify(notificationService).sendNotificationWithUserId(followerId, Constants.NOTIFICATION_NEW_FEED_TITLE, body);
        }
        for (UUID followerId : secondPage) {
            verify(notificationService).sendNotificationWithUserId(followerId, Constants.NOTIFICATION_NEW_FEED_TITLE, body);
        }
        verifyNoMoreInteractions(notificationService);
        InOrder order = inOrder(followRepository, notificationService);
        order.verify(followRepository).findActiveFollowerIdsByFollowedId(authorId, page);
        order.verify(notificationService).sendNotificationWithUserId(lastFollower, Constants.NOTIFICATION_NEW_FEED_TITLE, body);
        order.verify(followRepository).findActiveFollowerIdsByFollowedIdAfter(authorId, lastFollower, page);
        order.verify(notificationService).sendNotificationWithUserId(secondPage.getFirst(), Constants.NOTIFICATION_NEW_FEED_TITLE, body);
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
        doThrow(failure).when(notificationService).sendNotificationWithUserId(
                followerId, Constants.NOTIFICATION_NEW_FEED_TITLE,
                String.format(Constants.NOTIFICATION_NEW_FEED_BODY, "author"));

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
}
