package com.rizkysiregar.narrativ.messaging.consumer;

import com.rizkysiregar.narrativ.service.NotificationInboxService;
import com.rizkysiregar.narrativ.model.enums.NotificationType;
import com.rizkysiregar.narrativ.config.RabbitMQConfig;
import com.rizkysiregar.narrativ.messaging.event.CommentRepliedEvent;
import com.rizkysiregar.narrativ.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.narrativ.model.entity.UserDeviceEntity;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.UserStatus;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.UserDeviceService;
import com.rizkysiregar.narrativ.service.email.IEmailService;
import com.rizkysiregar.narrativ.service.firebase.FirebaseNotificationService;
import com.rizkysiregar.narrativ.service.firebase.PushTextResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentReplyConsumerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserDeviceService userDeviceService;
    @Mock
    private FirebaseNotificationService notificationService;
    @Mock
    private PushTextResolver textResolver;
    @Mock
    private IEmailService emailService;
    @Mock
    private EventDeduplicator deduplicator;
    @Mock
    private NotificationInboxService inboxService;

    @InjectMocks
    private CommentReplyConsumer consumer;

    private final UUID commentId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final UUID replierId = UUID.randomUUID();
    private final UUID answeredOwnerId = UUID.randomUUID();
    private final UUID postOwnerId = UUID.randomUUID();
    private CommentRepliedEvent event;

    @BeforeEach
    void setUp() {
        event = CommentRepliedEvent.builder()
                .commentId(commentId)
                .postId(postId)
                .replierId(replierId)
                .replierUsername("budi")
                .recipientId(answeredOwnerId)
                .postOwnerId(postOwnerId)
                .contentPreview("setuju banget")
                .build();
        lenient().when(deduplicator.tryReserve(eq(RabbitMQConfig.COMMENT_REPLY_CONSUMER), anyString())).thenReturn(true);
    }

    private UserEntity user(UUID id, UserStatus status) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername("u-" + id);
        user.setEmail(id + "@mail.com");
        user.setStatus(status);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    private void devices(UUID id, boolean has) {
        when(userDeviceService.getActiveDevice(id)).thenReturn(has ? List.of(new UserDeviceEntity()) : List.of());
    }

    private Map<String, String> data(String type, UUID actor) {
        return Map.of("type", type, "postId", postId.toString(), "commentId", commentId.toString(),
                "actorId", actor.toString(), "actorUsername", "budi");
    }

    @Test
    void replyTellsBothTheAnsweredCommentOwnerAndThePostOwner() {
        user(answeredOwnerId, UserStatus.ACTIVE);
        user(postOwnerId, UserStatus.ACTIVE);
        devices(answeredOwnerId, true);
        devices(postOwnerId, true);

        consumer.handleCommentReplied(event);

        verify(notificationService).sendNotificationToUserDevices(eq(answeredOwnerId), any(),
                eq(data("COMMENT_REPLIED", replierId)));
        verify(notificationService).sendNotificationToUserDevices(eq(postOwnerId), any(),
                eq(data("POST_COMMENTED", replierId)));
        verifyNoInteractions(emailService);
    }

    @Test
    void replyToThePostOwnersOwnCommentSendsOnlyOnePush() {
        event.setRecipientId(postOwnerId);
        user(postOwnerId, UserStatus.ACTIVE);
        devices(postOwnerId, true);

        consumer.handleCommentReplied(event);

        verify(notificationService).sendNotificationToUserDevices(eq(postOwnerId), any(),
                eq(data("COMMENT_REPLIED", replierId)));
        verify(notificationService, org.mockito.Mockito.times(1))
                .sendNotificationToUserDevices(any(), any(), any());
    }

    @Test
    void topLevelCommentTellsOnlyThePostOwner() {
        event.setRecipientId(null);
        user(postOwnerId, UserStatus.ACTIVE);
        devices(postOwnerId, true);

        consumer.handleCommentReplied(event);

        verify(notificationService).sendNotificationToUserDevices(eq(postOwnerId), any(),
                eq(data("POST_COMMENTED", replierId)));
    }

    @Test
    void theActorIsNeverTold() {
        event.setReplierId(postOwnerId);
        event.setRecipientId(null);

        consumer.handleCommentReplied(event);

        verifyNoInteractions(userRepository, notificationService, emailService);
    }

    @Test
    void anAnsweredCommentOwnerWithoutADeviceGetsAnEmailButAPostOwnerDoesNot() {
        user(answeredOwnerId, UserStatus.ACTIVE);
        user(postOwnerId, UserStatus.ACTIVE);
        devices(answeredOwnerId, false);
        devices(postOwnerId, false);

        consumer.handleCommentReplied(event);

        verify(emailService).sendEmailTemplate(eq(answeredOwnerId + "@mail.com"), eq("budi membalas komentar Anda"),
                eq("comment-reply"), any());
        verify(emailService, org.mockito.Mockito.times(1)).sendEmailTemplate(anyString(), anyString(), anyString(), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void skipsARecipientThatWasAlreadyProcessed() {
        when(deduplicator.tryReserve(RabbitMQConfig.COMMENT_REPLY_CONSUMER, commentId + ":" + answeredOwnerId))
                .thenReturn(false);
        user(postOwnerId, UserStatus.ACTIVE);
        devices(postOwnerId, true);

        consumer.handleCommentReplied(event);

        verify(notificationService, never()).sendNotificationToUserDevices(eq(answeredOwnerId), any(), any());
        verify(notificationService).sendNotificationToUserDevices(eq(postOwnerId), any(), any());
    }

    @Test
    void skipsABannedRecipient() {
        event.setRecipientId(null);
        user(postOwnerId, UserStatus.BANNED);

        consumer.handleCommentReplied(event);

        verifyNoInteractions(notificationService, emailService);
    }

    @Test
    void aFailureForOneRecipientDoesNotStopTheOtherAndIsRetried() {
        user(answeredOwnerId, UserStatus.ACTIVE);
        user(postOwnerId, UserStatus.ACTIVE);
        devices(answeredOwnerId, true);
        devices(postOwnerId, true);
        doThrow(new IllegalStateException("fcm down")).when(notificationService)
                .sendNotificationToUserDevices(eq(answeredOwnerId), any(), any());

        assertThrows(IllegalStateException.class, () -> consumer.handleCommentReplied(event));

        verify(notificationService).sendNotificationToUserDevices(eq(postOwnerId), any(), any());
        verify(deduplicator).release(RabbitMQConfig.COMMENT_REPLY_CONSUMER, commentId + ":" + answeredOwnerId);
        verify(deduplicator, never()).release(RabbitMQConfig.COMMENT_REPLY_CONSUMER, commentId + ":" + postOwnerId);
    }

    @Test
    void anOldEventWithoutPostOwnerStillTellsTheAnsweredCommentOwner() {
        event.setPostOwnerId(null);
        user(answeredOwnerId, UserStatus.ACTIVE);
        devices(answeredOwnerId, true);

        consumer.handleCommentReplied(event);

        verify(notificationService).sendNotificationToUserDevices(eq(answeredOwnerId), any(),
                eq(data("COMMENT_REPLIED", replierId)));
    }

    @Test
    void bothRecipientsGetTheCommentInTheirInboxEvenWithoutADevice() {
        user(answeredOwnerId, UserStatus.ACTIVE);
        user(postOwnerId, UserStatus.ACTIVE);
        devices(answeredOwnerId, false);
        devices(postOwnerId, false);

        consumer.handleCommentReplied(event);

        verify(inboxService).recordComment(answeredOwnerId, NotificationType.COMMENT_REPLIED, replierId, postId,
                commentId, "setuju banget");
        verify(inboxService).recordComment(postOwnerId, NotificationType.POST_COMMENTED, replierId, postId,
                commentId, "setuju banget");
    }
}
