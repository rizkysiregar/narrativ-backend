package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AdminNotificationTemplateDTO;
import com.rizkysiregar.feedzmore.model.entity.NotificationTemplateEntity;
import com.rizkysiregar.feedzmore.model.enums.AdminAuditAction;
import com.rizkysiregar.feedzmore.model.enums.AuditTargetType;
import com.rizkysiregar.feedzmore.model.enums.TemplateCategory;
import com.rizkysiregar.feedzmore.model.enums.TemplateMode;
import com.rizkysiregar.feedzmore.model.request.CreateNotificationTemplateRequest;
import com.rizkysiregar.feedzmore.model.request.UpdateNotificationTemplateRequest;
import com.rizkysiregar.feedzmore.repository.NotificationTemplateRepository;
import com.rizkysiregar.feedzmore.util.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminNotificationTemplateServiceTest {

    @Mock
    private NotificationTemplateRepository templateRepository;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AdminNotificationTemplateService service;

    private NotificationTemplateEntity template() {
        NotificationTemplateEntity entity = new NotificationTemplateEntity();
        entity.setId(UUID.randomUUID());
        entity.setCode("CASUAL_HELLO");
        entity.setTitle("Hai");
        entity.setBody("Ada yang baru");
        entity.setMode("STATIC");
        entity.setCategory("CASUAL");
        entity.setTopic("global");
        entity.setActive(true);
        return entity;
    }

    private void assertFails(GlobalMessage expected, HttpStatus status, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(expected.getMessage(), exception.getMessage());
        assertEquals(status, exception.getHttpStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_isActiveByDefaultStoresEnumNamesAndTurnsABlankTopicIntoNone() {
        when(templateRepository.saveAndFlush(any(NotificationTemplateEntity.class))).thenAnswer(invocation -> {
            NotificationTemplateEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AdminNotificationTemplateDTO result = service.create(new CreateNotificationTemplateRequest(
                "CASUAL_HELLO", " Hai ", " Ada yang baru ", TemplateMode.STATIC, TemplateCategory.CASUAL, "  ", null));

        assertEquals("Hai", result.title());
        assertEquals("STATIC", result.mode());
        assertEquals("CASUAL", result.category());
        assertNull(result.topic());
        assertTrue(result.active());
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(AdminAuditAction.TEMPLATE_CREATE), eq(AuditTargetType.NOTIFICATION_TEMPLATE),
                eq(result.id()), isNull(), metadata.capture());
        assertEquals("CASUAL_HELLO", metadata.getValue().get("code"));
    }

    @Test
    void create_canStartSwitchedOff() {
        when(templateRepository.saveAndFlush(any(NotificationTemplateEntity.class))).thenAnswer(invocation -> {
            NotificationTemplateEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        assertFalse(service.create(new CreateNotificationTemplateRequest(
                "EVENT_X", "t", "b", TemplateMode.DIRECT, TemplateCategory.EVENT, null, false)).active());
    }

    @Test
    void create_aCodeThatExistsIsAConflictAlsoWhenTwoCreatesRace() {
        CreateNotificationTemplateRequest request = new CreateNotificationTemplateRequest(
                "CASUAL_HELLO", "t", "b", TemplateMode.STATIC, TemplateCategory.CASUAL, null, null);
        when(templateRepository.existsByCode("CASUAL_HELLO")).thenReturn(true);
        assertFails(GlobalMessage.TEMPLATE_CODE_EXISTS, HttpStatus.CONFLICT, () -> service.create(request));
        verify(templateRepository, never()).saveAndFlush(any());

        when(templateRepository.existsByCode("CASUAL_HELLO")).thenReturn(false);
        when(templateRepository.saveAndFlush(any(NotificationTemplateEntity.class))).thenThrow(new DataIntegrityViolationException("uk"));
        assertFails(GlobalMessage.TEMPLATE_CODE_EXISTS, HttpStatus.CONFLICT, () -> service.create(request));
        verifyNoInteractions(auditLogService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void update_changesOnlyWhatWasGivenAndAuditsTheSwitch() {
        NotificationTemplateEntity entity = template();
        when(templateRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(templateRepository.save(any(NotificationTemplateEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminNotificationTemplateDTO result = service.update(entity.getId(),
                new UpdateNotificationTemplateRequest(" Halo ", null, null, TemplateCategory.EVENT, null, false));

        assertEquals("Halo", result.title());
        assertEquals("Ada yang baru", result.body());
        assertEquals("EVENT", result.category());
        assertFalse(result.active());
        assertEquals("CASUAL_HELLO", result.code());
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(AdminAuditAction.TEMPLATE_UPDATE), eq(AuditTargetType.NOTIFICATION_TEMPLATE),
                eq(entity.getId().toString()), isNull(), metadata.capture());
        assertEquals(List.of("title", "category", "active"), metadata.getValue().get("fields"));
        assertEquals(true, metadata.getValue().get("previousActive"));
        assertEquals(false, metadata.getValue().get("newActive"));
    }

    @Test
    void update_theSameValuesChangeNothingAndLeaveNoTrace() {
        NotificationTemplateEntity entity = template();
        when(templateRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        service.update(entity.getId(), new UpdateNotificationTemplateRequest("Hai", "Ada yang baru", TemplateMode.STATIC, TemplateCategory.CASUAL, "global", true));

        verify(templateRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void update_needsAtLeastOneFieldAndAnExistingTemplate() {
        UUID id = UUID.randomUUID();
        assertFails(GlobalMessage.NOTHING_TO_UPDATE, HttpStatus.BAD_REQUEST,
                () -> service.update(id, new UpdateNotificationTemplateRequest(null, null, null, null, null, null)));

        when(templateRepository.findById(id)).thenReturn(Optional.empty());
        assertFails(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND,
                () -> service.update(id, new UpdateNotificationTemplateRequest("x", null, null, null, null, null)));
        verify(templateRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void delete_removesItForGoodAndKeepsAFullSnapshotInTheAuditLog() {
        NotificationTemplateEntity entity = template();
        when(templateRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        assertEquals(Operation.DELETED, service.delete(entity.getId()));

        verify(templateRepository).delete(entity);
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(AdminAuditAction.TEMPLATE_DELETE), eq(AuditTargetType.NOTIFICATION_TEMPLATE),
                eq(entity.getId().toString()), isNull(), metadata.capture());
        Map<String, Object> snapshot = metadata.getValue();
        assertEquals("CASUAL_HELLO", snapshot.get("code"));
        assertEquals("Hai", snapshot.get("title"));
        assertEquals("Ada yang baru", snapshot.get("body"));
        assertEquals("STATIC", snapshot.get("mode"));
        assertEquals("CASUAL", snapshot.get("category"));
        assertEquals("global", snapshot.get("topic"));
        assertEquals(true, snapshot.get("active"));
    }

    @Test
    void get_anUnknownTemplateIsNotFound() {
        UUID id = UUID.randomUUID();
        when(templateRepository.findById(id)).thenReturn(Optional.empty());

        assertFails(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND, () -> service.get(id));
    }
}
