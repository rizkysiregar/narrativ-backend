package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AdminUserDetailDTO;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.enums.AdminAuditAction;
import com.rizkysiregar.feedzmore.model.enums.AuditTargetType;
import com.rizkysiregar.feedzmore.model.enums.ReportAction;
import com.rizkysiregar.feedzmore.model.enums.ReportTargetType;
import com.rizkysiregar.feedzmore.model.enums.Role;
import com.rizkysiregar.feedzmore.model.enums.UserStatus;
import com.rizkysiregar.feedzmore.model.request.AdminUpdateUserRequest;
import com.rizkysiregar.feedzmore.model.request.AdminUserSearchRequest;
import com.rizkysiregar.feedzmore.model.request.BanUserRequest;
import com.rizkysiregar.feedzmore.model.request.UnbanUserRequest;
import com.rizkysiregar.feedzmore.repository.CommentRepository;
import com.rizkysiregar.feedzmore.repository.FollowRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
class UserManagementServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AuthService authService;
    @Mock
    private PostCrudRepository postCrudRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private ReportClosingService reportClosingService;
    @Mock
    private ProfileImageUrlResolver profileImageUrlResolver;

    @InjectMocks
    private UserManagementService userManagementService;

    private final UUID adminId = UUID.randomUUID();

    private UserEntity user(UUID id, Role role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername("someone");
        user.setEmail("someone@mail.com");
        user.setFirstName("Some");
        user.setLastName("One");
        user.setRole(role);
        return user;
    }

    private void assertFails(GlobalMessage expected, HttpStatus status, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(expected.getMessage(), exception.getMessage());
        assertEquals(status, exception.getHttpStatus());
    }

    // ---------------------------------------------------------------- update

    @Test
    @SuppressWarnings("unchecked")
    void updateUserForAdmin_auditsChangedFieldNamesAndRoleChangeButNoValues() {
        UUID id = UUID.randomUUID();
        UserEntity existing = user(id, Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userManagementService.updateUserForAdmin(id.toString(),
                new AdminUpdateUserRequest(null, null, "Jane", null, null, null, Role.ADMIN), adminId);

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(AdminAuditAction.USER_UPDATE), eq(AuditTargetType.USER),
                eq(id.toString()), isNull(), metadata.capture());
        assertEquals(List.of("firstName", "role"), metadata.getValue().get("fields"));
        assertEquals(Role.USER, metadata.getValue().get("previousRole"));
        assertEquals(Role.ADMIN, metadata.getValue().get("newRole"));
        assertFalse(metadata.getValue().containsValue("Jane"));
    }

    @Test
    void updateUserForAdmin_adminCannotChangeOwnRole() {
        UserEntity self = user(adminId, Role.ADMIN);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(self));

        assertFails(GlobalMessage.CANNOT_CHANGE_OWN_ROLE, HttpStatus.FORBIDDEN, () ->
                userManagementService.updateUserForAdmin(adminId.toString(),
                        new AdminUpdateUserRequest(null, null, null, null, null, null, Role.USER), adminId));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updateUserForAdmin_sameRoleOnSelfIsNotARoleChange() {
        UserEntity self = user(adminId, Role.ADMIN);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(self));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userManagementService.updateUserForAdmin(adminId.toString(),
                new AdminUpdateUserRequest(null, null, "Jane", null, null, null, Role.ADMIN), adminId);

        assertEquals(Role.ADMIN, self.getRole());
    }

    @Test
    void updateUserForAdmin_cannotDemoteTheLastActiveAdmin() {
        UUID otherAdminId = UUID.randomUUID();
        UserEntity otherAdmin = user(otherAdminId, Role.ADMIN);
        when(userRepository.findById(otherAdminId)).thenReturn(Optional.of(otherAdmin));
        when(userRepository.countByRoleAndIsDeletedFalse(Role.ADMIN)).thenReturn(1L);

        assertFails(GlobalMessage.LAST_ADMIN, HttpStatus.CONFLICT, () ->
                userManagementService.updateUserForAdmin(otherAdminId.toString(),
                        new AdminUpdateUserRequest(null, null, null, null, null, null, Role.USER), adminId));

        verify(userRepository, never()).save(any());
    }

    @Test
    void banUser_marksBannedAuditsAndRemovesTheUserFromSearchButKeepsTheSessionsSoTheAppCanShowTheReason() {
        UUID id = UUID.randomUUID();
        UserEntity target = user(id, Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(target));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserDetailDTO result = userManagementService.banUserForAdmin(id.toString(),
                new BanUserRequest("  spam account  "), adminId);

        assertEquals(UserStatus.BANNED, target.getStatus());
        assertEquals(adminId, target.getBannedBy());
        assertNotNull(target.getBannedAt());
        assertEquals("spam account", target.getBanReason());
        assertEquals(UserStatus.BANNED, result.status());
        assertEquals("spam account", result.banReason());
        assertEquals(adminId.toString(), result.bannedBy());

        InOrder order = inOrder(userRepository, reportClosingService, auditLogService);
        order.verify(userRepository).save(target);
        order.verify(reportClosingService).closeOpenReports(ReportTargetType.USER, id, adminId, ReportAction.BAN);
        order.verify(auditLogService).record(AdminAuditAction.USER_BAN, AuditTargetType.USER, id.toString(),
                "spam account", null);
        verifyNoInteractions(authService);
    }

    @Test
    void banUser_rejectsSelfAdminDeletedAndAlreadyBannedTargets() {
        // self
        UserEntity self = user(adminId, Role.ADMIN);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(self));
        assertFails(GlobalMessage.CANNOT_BAN_SELF, HttpStatus.FORBIDDEN, () ->
                userManagementService.banUserForAdmin(adminId.toString(), new BanUserRequest("some reason"), adminId));

        // another admin
        UUID otherAdminId = UUID.randomUUID();
        when(userRepository.findById(otherAdminId)).thenReturn(Optional.of(user(otherAdminId, Role.ADMIN)));
        assertFails(GlobalMessage.CANNOT_BAN_ADMIN, HttpStatus.FORBIDDEN, () ->
                userManagementService.banUserForAdmin(otherAdminId.toString(), new BanUserRequest("some reason"), adminId));

        // deleted by the user
        UUID deletedId = UUID.randomUUID();
        UserEntity deleted = user(deletedId, Role.USER);
        deleted.setDeleted(true);
        when(userRepository.findById(deletedId)).thenReturn(Optional.of(deleted));
        assertFails(GlobalMessage.USER_ALREADY_DELETED, HttpStatus.CONFLICT, () ->
                userManagementService.banUserForAdmin(deletedId.toString(), new BanUserRequest("some reason"), adminId));

        // already banned
        UUID bannedId = UUID.randomUUID();
        UserEntity banned = user(bannedId, Role.USER);
        banned.setStatus(UserStatus.BANNED);
        when(userRepository.findById(bannedId)).thenReturn(Optional.of(banned));
        assertFails(GlobalMessage.USER_ALREADY_BANNED, HttpStatus.CONFLICT, () ->
                userManagementService.banUserForAdmin(bannedId.toString(), new BanUserRequest("some reason"), adminId));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditLogService, authService, reportClosingService);
    }

    @Test
    void banUser_unknownUserIsNotFoundAndInvalidIdIsBadRequest() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertFails(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND, () ->
                userManagementService.banUserForAdmin(id.toString(), new BanUserRequest("some reason"), adminId));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () ->
                userManagementService.banUserForAdmin("not-a-uuid", new BanUserRequest("some reason"), adminId));
    }

    // ----------------------------------------------------------------- unban

    @Test
    void unbanUser_clearsBanFieldsAuditsAndReindexes() {
        UUID id = UUID.randomUUID();
        UserEntity target = user(id, Role.USER);
        target.setStatus(UserStatus.BANNED);
        target.setBanReason("spam");
        target.setBannedBy(adminId);
        target.setBannedAt(java.time.Instant.now());
        when(userRepository.findById(id)).thenReturn(Optional.of(target));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserDetailDTO result = userManagementService.unbanUserForAdmin(id.toString(), new UnbanUserRequest("appeal accepted"));

        assertEquals(UserStatus.ACTIVE, target.getStatus());
        assertNull(target.getBanReason());
        assertNull(target.getBannedBy());
        assertNull(target.getBannedAt());
        assertEquals(UserStatus.ACTIVE, result.status());
        verify(auditLogService).record(AdminAuditAction.USER_UNBAN, AuditTargetType.USER, id.toString(),
                "appeal accepted", null);
        // sessions from before the ban must not silently come back
        verify(authService).revokeRefreshTokens(id);
    }

    @Test
    void unbanUser_withoutBodyStillWorksAndNotBannedIsConflict() {
        UUID id = UUID.randomUUID();
        UserEntity target = user(id, Role.USER);
        target.setStatus(UserStatus.BANNED);
        when(userRepository.findById(id)).thenReturn(Optional.of(target));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userManagementService.unbanUserForAdmin(id.toString(), null);
        verify(auditLogService).record(AdminAuditAction.USER_UNBAN, AuditTargetType.USER, id.toString(), null, null);

        // now ACTIVE: a second unban changes nothing
        assertFails(GlobalMessage.USER_NOT_BANNED, HttpStatus.CONFLICT, () ->
                userManagementService.unbanUserForAdmin(id.toString(), null));
    }

    @Test
    void forceLogout_revokesRefreshTokensAndAudits() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, Role.USER)));

        userManagementService.forceLogoutForAdmin(id.toString());

        verify(authService).revokeRefreshTokens(id);
        verify(auditLogService).record(AdminAuditAction.USER_FORCE_LOGOUT, AuditTargetType.USER, id.toString(), null, null);
    }

    // ---------------------------------------------------------------- detail

    @Test
    void getUserByIdForAdmin_includesRawCounts() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, Role.USER)));
        when(postCrudRepository.countByUserIdAndIsDeletedFalse(id)).thenReturn(3L);
        when(commentRepository.countByUserId(id)).thenReturn(5L);
        when(followRepository.countByFollowedId(id)).thenReturn(7L);
        when(followRepository.countByFollowerId(id)).thenReturn(11L);

        AdminUserDetailDTO result = userManagementService.getUserByIdForAdmin(id.toString());

        assertEquals(new AdminUserDetailDTO.Stats(3, 5, 7, 11), result.stats());
    }

    // ----------------------------------------------------------------- list

    @Test
    void searchUsersForAdmin_rejectsInvalidFilters() {
        PageRequest page = PageRequest.of(0, 20);

        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> userManagementService.searchUsersForAdmin(
                new AdminUserSearchRequest(null, null, "SUPERUSER", null, null, null), page));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> userManagementService.searchUsersForAdmin(
                new AdminUserSearchRequest(null, null, null, "SLEEPING", null, null), page));
        assertFails(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST, () -> userManagementService.searchUsersForAdmin(
                new AdminUserSearchRequest(null, null, null, null, LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 7)), page));

        verifyNoInteractions(userRepository);
    }
}
