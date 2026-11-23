package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.AdminParameterDTO;
import com.rizkysiregar.narrativ.model.entity.ParameterEntity;
import com.rizkysiregar.narrativ.model.enums.AdminAuditAction;
import com.rizkysiregar.narrativ.model.enums.AuditTargetType;
import com.rizkysiregar.narrativ.model.request.CreateParameterRequest;
import com.rizkysiregar.narrativ.model.request.UpdateParameterRequest;
import com.rizkysiregar.narrativ.repository.ParameterRepository;
import com.rizkysiregar.narrativ.util.Operation;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminParameterServiceTest {

    @Mock
    private ParameterRepository parameterRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private FeatureFlagService featureFlagService;

    @InjectMocks
    private AdminParameterService service;

    private ParameterEntity parameter(long id, String key, String value, String description) {
        ParameterEntity entity = new ParameterEntity();
        entity.setId(id);
        entity.setParamKey(key);
        entity.setParamValue(value);
        entity.setDescription(description);
        return entity;
    }

    private void assertFails(GlobalMessage expected, HttpStatus status, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(expected.getMessage(), exception.getMessage());
        assertEquals(status, exception.getHttpStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_savesTrimmedValuesAndAudits() {
        when(parameterRepository.saveAndFlush(any(ParameterEntity.class))).thenAnswer(invocation -> {
            ParameterEntity saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        AdminParameterDTO result = service.create(new CreateParameterRequest("maintenance.mode", "  false ", " flag for the app "));

        assertEquals(7L, result.id());
        assertEquals("false", result.paramValue());
        assertEquals("flag for the app", result.description());
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(AdminAuditAction.PARAMETER_CREATE), eq(AuditTargetType.PARAMETER), eq("7"), isNull(), metadata.capture());
        assertEquals("maintenance.mode", metadata.getValue().get("paramKey"));
        assertEquals("false", metadata.getValue().get("paramValue"));
    }

    @Test
    void create_aKeyThatExistsIsAConflictAlsoWhenTwoCreatesRace() {
        CreateParameterRequest request = new CreateParameterRequest("maintenance.mode", "false", "flag");
        when(parameterRepository.existsByParamKey("maintenance.mode")).thenReturn(true);
        assertFails(GlobalMessage.PARAMETER_KEY_EXISTS, HttpStatus.CONFLICT, () -> service.create(request));
        verify(parameterRepository, never()).saveAndFlush(any());

        when(parameterRepository.existsByParamKey("maintenance.mode")).thenReturn(false);
        when(parameterRepository.saveAndFlush(any(ParameterEntity.class))).thenThrow(new DataIntegrityViolationException("uk"));
        assertFails(GlobalMessage.PARAMETER_KEY_EXISTS, HttpStatus.CONFLICT, () -> service.create(request));
        verifyNoInteractions(auditLogService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void update_changesOnlyWhatWasGivenAndAuditsTheOldAndNewValue() {
        ParameterEntity entity = parameter(3L, "maintenance.mode", "false", "flag");
        when(parameterRepository.findById(3L)).thenReturn(Optional.of(entity));
        when(parameterRepository.save(any(ParameterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminParameterDTO result = service.update(3L, new UpdateParameterRequest(" true ", null));

        assertEquals("true", result.paramValue());
        assertEquals("flag", result.description());
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(AdminAuditAction.PARAMETER_UPDATE), eq(AuditTargetType.PARAMETER), eq("3"), isNull(), metadata.capture());
        assertEquals(List.of("paramValue"), metadata.getValue().get("fields"));
        assertEquals("false", metadata.getValue().get("previousValue"));
        assertEquals("true", metadata.getValue().get("newValue"));
        assertEquals("maintenance.mode", metadata.getValue().get("paramKey"));
    }

    @Test
    void update_theSameValuesChangeNothingAndLeaveNoTrace() {
        ParameterEntity entity = parameter(3L, "maintenance.mode", "false", "flag");
        when(parameterRepository.findById(3L)).thenReturn(Optional.of(entity));

        AdminParameterDTO result = service.update(3L, new UpdateParameterRequest("false", "flag"));

        assertEquals("false", result.paramValue());
        verify(parameterRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void update_needsAtLeastOneFieldAndAnExistingParameter() {
        assertFails(GlobalMessage.NOTHING_TO_UPDATE, HttpStatus.BAD_REQUEST, () -> service.update(3L, new UpdateParameterRequest(null, null)));

        when(parameterRepository.findById(99L)).thenReturn(Optional.empty());
        assertFails(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND, () -> service.update(99L, new UpdateParameterRequest("x", null)));
        verify(parameterRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void delete_removesItForGoodAndKeepsWhatItWasInTheAuditLog() {
        ParameterEntity entity = parameter(3L, "maintenance.mode", "false", "flag");
        when(parameterRepository.findById(3L)).thenReturn(Optional.of(entity));

        assertEquals(Operation.DELETED, service.delete(3L));

        verify(parameterRepository).delete(entity);
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(AdminAuditAction.PARAMETER_DELETE), eq(AuditTargetType.PARAMETER), eq("3"), isNull(), metadata.capture());
        assertEquals("maintenance.mode", metadata.getValue().get("paramKey"));
        assertEquals("false", metadata.getValue().get("paramValue"));
        assertEquals("flag", metadata.getValue().get("description"));
    }

    @Test
    void delete_anUnknownParameterIsNotFound() {
        when(parameterRepository.findById(99L)).thenReturn(Optional.empty());

        assertFails(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND, () -> service.delete(99L));

        verify(parameterRepository, never()).delete(any(ParameterEntity.class));
        verifyNoInteractions(auditLogService);
    }

    private void assertInvalidFlag(Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(GlobalMessage.INVALID_PARAMETER.name(), exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void create_featureFlag_normalizesTheValueAndDropsTheCache() {
        when(parameterRepository.saveAndFlush(any(ParameterEntity.class))).thenAnswer(invocation -> {
            ParameterEntity saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        AdminParameterDTO result = service.create(new CreateParameterRequest("feature.chat.enabled", " TRUE ", "Chat"));

        assertEquals("true", result.paramValue());
        verify(featureFlagService).evictAfterCommit();
    }

    @Test
    void create_featureFlagWithAnotherValue_isRejected() {
        assertInvalidFlag(() -> service.create(new CreateParameterRequest("feature.chat.enabled", "yes", "Chat")));

        verify(parameterRepository, never()).saveAndFlush(any());
        verifyNoInteractions(featureFlagService);
    }

    @Test
    void update_featureFlag_dropsTheCache() {
        when(parameterRepository.findById(3L)).thenReturn(Optional.of(parameter(3L, "feature.chat.enabled", "true", "Chat")));
        when(parameterRepository.save(any(ParameterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminParameterDTO result = service.update(3L, new UpdateParameterRequest("False", null));

        assertEquals("false", result.paramValue());
        verify(featureFlagService).evictAfterCommit();
    }

    @Test
    void update_featureFlagWithAnotherValue_isRejected() {
        when(parameterRepository.findById(3L)).thenReturn(Optional.of(parameter(3L, "feature.chat.enabled", "true", "Chat")));

        assertInvalidFlag(() -> service.update(3L, new UpdateParameterRequest("1", null)));

        verify(parameterRepository, never()).save(any());
    }

    @Test
    void update_otherParameter_leavesTheFeatureCacheAlone() {
        when(parameterRepository.findById(4L)).thenReturn(Optional.of(parameter(4L, "feed.ranking.gravity", "1.5", "Gravity")));
        when(parameterRepository.save(any(ParameterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(4L, new UpdateParameterRequest("2", null));

        verifyNoInteractions(featureFlagService);
    }

    @Test
    void delete_featureFlag_dropsTheCache() {
        when(parameterRepository.findById(5L)).thenReturn(Optional.of(parameter(5L, "feature.chat.enabled", "true", "Chat")));

        service.delete(5L);

        verify(featureFlagService).evictAfterCommit();
    }
}
