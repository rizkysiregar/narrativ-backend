package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.model.entity.PostMediaEntity;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.entity.UserNotificationEntity;
import com.rizkysiregar.narrativ.model.enums.MediaType;
import com.rizkysiregar.narrativ.model.enums.NotificationType;
import com.rizkysiregar.narrativ.model.response.NotificationResponse;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.repository.PostMediaRepository;
import com.rizkysiregar.narrativ.repository.UserNotificationRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.util.InboxCursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationInboxServiceTest {

    @Mock
    private UserNotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostMediaRepository postMediaRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private ProfileImageUrlResolver profileImageUrlResolver;

    private NotificationInboxService inboxService;

    private final Instant now = Instant.parse("2026-11-19T05:00:00Z");
    private final UUID recipientId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        inboxService = new NotificationInboxService(notificationRepository, userRepository, postMediaRepository,
                fileStorageService, profileImageUrlResolver, Clock.fixed(now, ZoneId.of("Asia/Jakarta")));
    }

    private UserNotificationEntity saved() {
        ArgumentCaptor<UserNotificationEntity> row = ArgumentCaptor.forClass(UserNotificationEntity.class);
        verify(notificationRepository).saveAndFlush(row.capture());
        return row.getValue();
    }

    @Test
    void aLikeIsGroupedWithTheOtherLikesOfThePostThatDay() {
        when(userRepository.getReferenceById(actorId)).thenReturn(new UserEntity());

        inboxService.recordLike(recipientId, actorId, postId);

        UserNotificationEntity row = saved();
        assertEquals(NotificationType.POST_LIKED, row.getType());
        assertEquals("like:" + postId + ":" + actorId, row.getDedupKey());
        assertEquals("like:" + postId + ":2026-11-19", row.getGroupKey());
        assertEquals(now, row.getCreatedAt());
    }

    @Test
    void aCommentIsItsOwnEntryWithAShortPreview() {
        UUID commentId = UUID.randomUUID();
        when(userRepository.getReferenceById(actorId)).thenReturn(new UserEntity());

        inboxService.recordComment(recipientId, NotificationType.POST_COMMENTED, actorId, postId, commentId, "x".repeat(300));

        UserNotificationEntity row = saved();
        assertEquals(row.getDedupKey(), row.getGroupKey());
        assertEquals(commentId, row.getCommentId());
        assertEquals(NotificationInboxService.PREVIEW_LENGTH, row.getPreview().length());
    }

    @Test
    void whatYouDidYourselfIsNotRecorded() {
        inboxService.recordFollow(actorId, actorId);

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void aRedeliveredEventAddsNothing() {
        when(notificationRepository.existsByDedupKey(any())).thenReturn(true);

        inboxService.recordFollow(recipientId, actorId);

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void aRowWrittenMeanwhileIsNotAnError() {
        when(userRepository.getReferenceById(actorId)).thenReturn(new UserEntity());
        when(notificationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        inboxService.recordFollow(recipientId, actorId);
    }

    @Test
    void unlikeAndUnfollowRemoveTheirRows() {
        inboxService.removeLike(postId, actorId);
        inboxService.removeFollow(recipientId, actorId);

        verify(notificationRepository).deleteByDedupKey("like:" + postId + ":" + actorId);
        verify(notificationRepository).deleteByDedupKey("follow:" + actorId + ":" + recipientId);
    }

    private UserNotificationRepository.GroupRow group(String key, long micros, long count, UUID latest, UUID second) {
        return new UserNotificationRepository.GroupRow() {
            public String getGroupKey() { return key; }
            public Long getLastAtMicros() { return micros; }
            public Long getActorCount() { return count; }
            public Boolean getUnread() { return true; }
            public UUID getLatestId() { return latest; }
            public UUID getSecondId() { return second; }
        };
    }

    private UserNotificationEntity row(UUID id, String username) {
        UserEntity actor = new UserEntity();
        actor.setId(UUID.randomUUID());
        actor.setUsername(username);
        UserNotificationEntity row = new UserNotificationEntity();
        row.setId(id);
        row.setType(NotificationType.POST_LIKED);
        row.setActor(actor);
        row.setPostId(postId);
        row.setCreatedAt(now);
        return row;
    }

    @Test
    void aPageShowsTheNewestTwoActorsTheTotalAndTheThumbnailAndPointsToTheNextPage() throws Exception {
        UUID latest = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String key = "like:" + postId + ":2026-11-19";
        when(notificationRepository.findGroups(recipientId, Long.MAX_VALUE, "", 2)).thenReturn(List.of(
                group(key, 100L, 4, latest, second),
                group("comment:x", 50L, 1, UUID.randomUUID(), null)));
        when(notificationRepository.findWithActorByIdIn(anyCollection()))
                .thenReturn(List.of(row(latest, "ani"), row(second, "budi")));
        PostMediaEntity media = new PostMediaEntity();
        media.setPostId(postId);
        media.setMediaType(MediaType.VIDEO);
        media.setThumbnailKey("thumb.jpg");
        when(postMediaRepository.findByPostIdInOrderByPostIdAscPositionAsc(anyCollection())).thenReturn(List.of(media));
        when(fileStorageService.getPresignUrl("thumb.jpg")).thenReturn("https://cdn/thumb.jpg");

        PaginatedResponse<NotificationResponse> page = inboxService.list(recipientId, null, 1);

        assertEquals(1, page.getContent().size());
        NotificationResponse entry = page.getContent().getFirst();
        assertEquals(List.of("ani", "budi"), entry.actors().stream().map(NotificationResponse.Actor::username).toList());
        assertEquals(4, entry.actorCount());
        assertEquals("https://cdn/thumb.jpg", entry.postThumbnailUrl());
        assertTrue(entry.unread());
        assertEquals(new InboxCursor(100L, key), InboxCursor.decode(page.getNextCursor()));
    }

    @Test
    void theLastPageHasNoCursor() {
        when(notificationRepository.findGroups(eq(recipientId), eq(Long.MAX_VALUE), eq(""), eq(21))).thenReturn(List.of());

        PaginatedResponse<NotificationResponse> page = inboxService.list(recipientId, null, 20);

        assertTrue(page.isLast());
        assertNull(page.getNextCursor());
    }
}
