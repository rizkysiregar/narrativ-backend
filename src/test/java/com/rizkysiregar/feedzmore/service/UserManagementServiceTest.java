package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.AdminAuditAction;
import com.rizkysiregar.feedzmore.model.enums.AuditTargetType;
import com.rizkysiregar.feedzmore.model.enums.Role;
import com.rizkysiregar.feedzmore.model.request.AdminUpdateUserRequest;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSearchService userSearchService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserManagementService userManagementService;

    private UserEntity user(UUID id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername("someone");
        user.setEmail("someone@mail.com");
        user.setRole(Role.USER);
        return user;
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateUserForAdmin_auditsChangedFieldNamesAndRoleChangeButNoValues() {
        UUID id = UUID.randomUUID();
        UserEntity existing = user(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userManagementService.updateUserForAdmin(id.toString(),
                new AdminUpdateUserRequest(null, null, "Jane", null, null, null, Role.ADMIN));

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(AdminAuditAction.USER_UPDATE), eq(AuditTargetType.USER),
                eq(id.toString()), isNull(), metadata.capture());
        assertEquals(List.of("firstName", "role"), metadata.getValue().get("fields"));
        assertEquals(Role.USER, metadata.getValue().get("previousRole"));
        assertEquals(Role.ADMIN, metadata.getValue().get("newRole"));
        assertFalse(metadata.getValue().containsValue("Jane"));
    }

    @Test
    void softDeleteUserForAdmin_recordsAuditOnlyWhenStateChanges() {
        UUID id = UUID.randomUUID();
        UserEntity existing = user(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));

        userManagementService.softDeleteUserForAdmin(id.toString());
        assertTrue(existing.isDeleted());
        verify(auditLogService).record(AdminAuditAction.USER_SOFT_DELETE, AuditTargetType.USER, id.toString(), null, null);

        // already deleted: nothing changes, so nothing is audited a second time
        org.mockito.Mockito.clearInvocations(auditLogService);
        userManagementService.softDeleteUserForAdmin(id.toString());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void restoreUserForAdmin_recordsAuditOnlyWhenStateChanges() {
        UUID id = UUID.randomUUID();
        UserEntity existing = user(id);
        existing.setDeleted(true);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userManagementService.restoreUserForAdmin(id.toString());
        assertFalse(existing.isDeleted());
        verify(auditLogService).record(AdminAuditAction.USER_RESTORE, AuditTargetType.USER, id.toString(), null, null);

        org.mockito.Mockito.clearInvocations(auditLogService);
        userManagementService.restoreUserForAdmin(id.toString());
        verifyNoInteractions(auditLogService);
    }
}
