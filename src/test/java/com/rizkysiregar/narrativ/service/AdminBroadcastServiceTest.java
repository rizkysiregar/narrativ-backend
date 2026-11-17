package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.AdminBroadcastDTO;
import com.rizkysiregar.narrativ.model.entity.NotificationBroadcastEntity;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.AdminAuditAction;
import com.rizkysiregar.narrativ.model.enums.AuditTargetType;
import com.rizkysiregar.narrativ.model.enums.BroadcastStatus;
import com.rizkysiregar.narrativ.model.request.BroadcastNotificationRequest;
import com.rizkysiregar.narrativ.repository.NotificationBroadcastRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.firebase.FirebaseNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBroadcastServiceTest {

    @Mock
    private FirebaseNotificationService firebaseNotificationService;
    @Mock
    private NotificationBroadcastRepository broadcastRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private AdminBroadcastService service;
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        service = new AdminBroadcastService(firebaseNotificationService, broadcastRepository, userRepository, auditLogService, transactionTemplate);
        // run the transaction callback straight away, there is no database here
        org.mockito.Mockito.lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback) invocation.getArgument(0)).doInTransaction(null));
        org.mockito.Mockito.lenient().when(broadcastRepository.save(any(NotificationBroadcastEntity.class))).thenAnswer(invocation -> {
            NotificationBroadcastEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    private BroadcastNotificationRequest request() {
        BroadcastNotificationRequest request = new BroadcastNotificationRequest();
        request.setTopic("global");
        request.setTitle("Halo semua");
        request.setBody("Ada fitur baru");
        return request;
    }

    @Test
    @SuppressWarnings("unchecked")
    void broadcast_sendsFirstThenRecordsTheHistoryAndTheAudit() {
        when(firebaseNotificationService.sendNotificationToTopic("global", "Halo semua", "Ada fitur baru"))
                .thenReturn(Mono.just("{\"name\":\"projects/narrativ/messages/1\"}"));

        NotificationBroadcastEntity result = service.broadcast(request(), adminId);

        assertEquals(BroadcastStatus.SENT, result.getStatus());
        assertEquals("{\"name\":\"projects/narrativ/messages/1\"}", result.getFcmResponse());
        assertEquals(adminId, result.getAdminId());
        assertEquals("global", result.getTopic());
        assertNull(result.getError());
        InOrder order = inOrder(firebaseNotificationService, broadcastRepository, auditLogService);
        order.verify(firebaseNotificationService).sendNotificationToTopic("global", "Halo semua", "Ada fitur baru");
        order.verify(broadcastRepository).save(any(NotificationBroadcastEntity.class));
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        order.verify(auditLogService).record(eq(AdminAuditAction.BROADCAST_SEND), eq(AuditTargetType.BROADCAST),
                eq(result.getId().toString()), isNull(), metadata.capture());
        assertEquals("SENT", metadata.getValue().get("status"));
        assertEquals("global", metadata.getValue().get("topic"));
        assertEquals("Halo semua", metadata.getValue().get("title"));
    }

    @Test
    void broadcast_anEmptyAnswerFromFirebaseIsStillASentMessage() {
        when(firebaseNotificationService.sendNotificationToTopic(any(), any(), any())).thenReturn(Mono.empty());

        NotificationBroadcastEntity result = service.broadcast(request(), adminId);

        assertEquals(BroadcastStatus.SENT, result.getStatus());
        assertEquals("", result.getFcmResponse());
    }

    @Test
    @SuppressWarnings("unchecked")
    void broadcast_whenFirebaseRefusesItIsStillRecordedAndTheAdminGets502() {
        String longError = "x".repeat(900);
        when(firebaseNotificationService.sendNotificationToTopic(any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException(longError)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.broadcast(request(), adminId));

        assertEquals(GlobalMessage.BROADCAST_FAILED.getMessage(), exception.getMessage());
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getHttpStatus());
        ArgumentCaptor<NotificationBroadcastEntity> saved = ArgumentCaptor.forClass(NotificationBroadcastEntity.class);
        verify(broadcastRepository).save(saved.capture());
        assertEquals(BroadcastStatus.FAILED, saved.getValue().getStatus());
        assertEquals(500, saved.getValue().getError().length());
        assertNull(saved.getValue().getFcmResponse());
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(AdminAuditAction.BROADCAST_SEND), eq(AuditTargetType.BROADCAST), any(), isNull(), metadata.capture());
        assertEquals("FAILED", metadata.getValue().get("status"));
    }

    @Test
    void toDto_carriesTheUsernameOfTheAdmin() {
        UserEntity admin = new UserEntity();
        admin.setId(adminId);
        admin.setUsername("admin1");
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        NotificationBroadcastEntity entity = NotificationBroadcastEntity.builder().id(UUID.randomUUID()).adminId(adminId)
                .topic("global").title("t").body("b").status(BroadcastStatus.SENT).createdAt(Instant.now()).build();

        AdminBroadcastDTO dto = service.toDto(entity);

        assertEquals("admin1", dto.adminUsername());
        assertEquals(BroadcastStatus.SENT, dto.status());
    }

    @Test
    void search_aBackwardsDateRangeIsABadRequest() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.search(
                null, null, null, LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 7), PageRequest.of(0, 20)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        verify(broadcastRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class));
        verifyNoInteractions(userRepository);
    }
}
