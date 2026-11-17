package com.rizkysiregar.narrativ.messaging.consumer;

import com.rizkysiregar.narrativ.config.RabbitMQConfig;
import com.rizkysiregar.narrativ.constant.Constants;
import com.rizkysiregar.narrativ.messaging.event.CommentRepliedEvent;
import com.rizkysiregar.narrativ.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.narrativ.model.entity.UserDeviceEntity;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.UserStatus;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.UserDeviceService;
import com.rizkysiregar.narrativ.service.email.IEmailService;
import com.rizkysiregar.narrativ.service.firebase.FirebaseNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
class CommentReplyConsumerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserDeviceService userDeviceService;
    @Mock
    private FirebaseNotificationService notificationService;
    @Mock
    private IEmailService emailService;
    @Mock
    private EventDeduplicator deduplicator;

    @InjectMocks
    private CommentReplyConsumer consumer;

    private final UUID commentId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private CommentRepliedEvent event;
    private UserEntity recipient;

    @BeforeEach
    void setUp() {
        event = CommentRepliedEvent.builder()
                .commentId(commentId)
                .postId(UUID.randomUUID())
                .replierId(UUID.randomUUID())
                .replierUsername("budi")
                .recipientId(recipientId)
                .contentPreview("setuju banget")
                .build();
        recipient = new UserEntity();
        recipient.setId(recipientId);
        recipient.setUsername("ani");
        recipient.setEmail("ani@mail.com");
        recipient.setStatus(UserStatus.ACTIVE);
    }

    private void reserve(boolean first) {
        when(deduplicator.tryReserve(RabbitMQConfig.COMMENT_REPLY_CONSUMER, commentId.toString())).thenReturn(first);
    }

    @Test
    void pushesWhenTheRecipientHasAnActiveDevice() {
        reserve(true);
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(userDeviceService.getActiveDevice(recipientId)).thenReturn(List.of(new UserDeviceEntity()));

        consumer.handleCommentReplied(event);

        verify(notificationService).sendNotificationWithUserId(recipientId,
                Constants.NOTIFICATION_COMMENT_REPLY_TITLE, "budi membalas komentar Anda: setuju banget");
        verifyNoInteractions(emailService);
    }

    @Test
    void emailsWhenTheRecipientHasNoActiveDevice() {
        reserve(true);
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(userDeviceService.getActiveDevice(recipientId)).thenReturn(List.of());

        consumer.handleCommentReplied(event);

        verify(emailService).sendEmailTemplate(eq("ani@mail.com"), eq("budi membalas komentar Anda"),
                eq("comment-reply"), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void skipsAlreadyProcessedEvent() {
        reserve(false);

        consumer.handleCommentReplied(event);

        verifyNoInteractions(userRepository, notificationService, emailService);
    }

    @Test
    void skipsWhenTheReplierIsTheRecipient() {
        event.setReplierId(recipientId);

        consumer.handleCommentReplied(event);

        verifyNoInteractions(deduplicator, notificationService, emailService);
    }

    @Test
    void skipsABannedRecipient() {
        reserve(true);
        recipient.setStatus(UserStatus.BANNED);
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));

        consumer.handleCommentReplied(event);

        verifyNoInteractions(notificationService, emailService);
    }

    @Test
    void releasesReservationAndRethrowsWhenEmailFails() {
        reserve(true);
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(userDeviceService.getActiveDevice(recipientId)).thenReturn(List.of());
        doThrow(new IllegalStateException("smtp down")).when(emailService)
                .sendEmailTemplate(anyString(), anyString(), anyString(), any());

        assertThrows(IllegalStateException.class, () -> consumer.handleCommentReplied(event));

        verify(deduplicator).release(RabbitMQConfig.COMMENT_REPLY_CONSUMER, commentId.toString());
    }
}
