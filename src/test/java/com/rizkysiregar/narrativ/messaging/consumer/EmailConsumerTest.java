package com.rizkysiregar.narrativ.messaging.consumer;

import com.rizkysiregar.narrativ.config.RabbitMQConfig;
import com.rizkysiregar.narrativ.messaging.event.PostCreatedEvent;
import com.rizkysiregar.narrativ.messaging.idempotency.EventDeduplicator;
import com.rizkysiregar.narrativ.service.email.IEmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailConsumerTest {

    @Mock
    private IEmailService emailService;
    @Mock
    private EventDeduplicator deduplicator;

    @InjectMocks
    private EmailConsumer consumer;

    @Test
    void sendsEmailAndReservesEvent() {
        UUID postId = UUID.randomUUID();
        PostCreatedEvent event = PostCreatedEvent.builder()
                .postId(postId)
                .email("author@mail.com")
                .emailProps(Map.of("username", "author"))
                .build();
        when(deduplicator.tryReserve(RabbitMQConfig.EMAIL_CONSUMER, postId.toString())).thenReturn(true);

        consumer.handlePostCreated(event);

        verify(emailService).sendEmailTemplate(eq("author@mail.com"), eq("Your post status"),
                eq("post-status"), any());
    }

    @Test
    void skipsAlreadyProcessedEvent() {
        UUID postId = UUID.randomUUID();
        PostCreatedEvent event = PostCreatedEvent.builder().postId(postId).build();
        when(deduplicator.tryReserve(RabbitMQConfig.EMAIL_CONSUMER, postId.toString())).thenReturn(false);

        consumer.handlePostCreated(event);

        verifyNoInteractions(emailService);
    }

    @Test
    void releasesReservationAndRethrowsWhenEmailFails() {
        UUID postId = UUID.randomUUID();
        PostCreatedEvent event = PostCreatedEvent.builder().postId(postId).build();
        IllegalStateException failure = new IllegalStateException("smtp down");
        when(deduplicator.tryReserve(RabbitMQConfig.EMAIL_CONSUMER, postId.toString())).thenReturn(true);
        doThrow(failure).when(emailService).sendEmailTemplate(any(), anyString(), anyString(), any());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> consumer.handlePostCreated(event));

        assertSame(failure, thrown);
        verify(deduplicator).release(RabbitMQConfig.EMAIL_CONSUMER, postId.toString());
    }

    @Test
    void processesEventWithoutPostIdWithoutReservation() {
        PostCreatedEvent event = PostCreatedEvent.builder().email("author@mail.com").build();

        consumer.handlePostCreated(event);

        verify(emailService).sendEmailTemplate(any(), anyString(), anyString(), any());
        verify(deduplicator, never()).tryReserve(anyString(), anyString());
    }
}
