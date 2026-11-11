package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AdminUserDetailDTO;
import com.rizkysiregar.feedzmore.model.dto.UserDetailDTO;
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
import com.rizkysiregar.feedzmore.model.request.UpdateMyUserRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.CommentRepository;
import com.rizkysiregar.feedzmore.repository.FollowRepository;
import com.rizkysiregar.feedzmore.repository.PostCrudRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.util.AdminDates;
import com.rizkysiregar.feedzmore.util.Operation;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final UserSearchService userSearchService;
    private final AuditLogService auditLogService;
    private final AuthService authService;
    private final PostCrudRepository postCrudRepository;
    private final CommentRepository commentRepository;
    private final FollowRepository followRepository;
    private final ReportClosingService reportClosingService;
    private final ProfileImageUrlResolver profileImageUrlResolver;

    public UserDetailDTO getMyProfile(UUID currentUserId) {
        UserEntity user = findActiveUserOrThrow(currentUserId);
        return toUserDetail(user);
    }

    @Transactional
    public UserDetailDTO updateMyProfile(UUID currentUserId, UpdateMyUserRequest request) {
        UserEntity user = findActiveUserOrThrow(currentUserId);

        applyCommonUserUpdates(
                user,
                request.username(),
                request.email(),
                request.firstName(),
                request.lastName(),
                request.phoneNumber(),
                null // the picture only changes through ProfileImageService
        );

        UserEntity savedUser = userRepository.save(user);
        userSearchService.indexUser(savedUser);

        return toUserDetail(savedUser);
    }

    @Transactional
    public Operation deleteMyAccount(UUID currentUserId) {
        UserEntity user = findActiveUserOrThrow(currentUserId);

        user.setDeleted(true);
        userRepository.save(user);
        userSearchService.deleteUser(user.getId().toString());

        return Operation.DELETED;
    }


    public PaginatedResponse<AdminUserDetailDTO> searchUsersForAdmin(AdminUserSearchRequest request, Pageable pageable) {
        log.info("Admin search users request: {}", request);

        Role role = parseEnum(Role.class, request.role());
        String statusFilter = request.status() == null || request.status().isBlank()
                ? null : request.status().trim().toUpperCase();
        if (statusFilter != null && !List.of("ACTIVE", "BANNED", "DELETED").contains(statusFilter)) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        if (request.createdFrom() != null && request.createdTo() != null
                && request.createdFrom().isAfter(request.createdTo())) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
        LocalDateTime createdFrom = AdminDates.startInclusive(request.createdFrom());
        LocalDateTime createdBefore = AdminDates.endExclusive(request.createdTo());

        Page<UserEntity> users = userRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.username() != null && !request.username().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("username")),
                        "%" + request.username().trim().toLowerCase() + "%"
                ));
            }
            if (request.email() != null && !request.email().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("email")),
                        "%" + request.email().trim().toLowerCase() + "%"
                ));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if ("DELETED".equals(statusFilter)) {
                predicates.add(cb.isTrue(root.get("isDeleted")));
            } else if (statusFilter != null) {
                predicates.add(cb.isFalse(root.get("isDeleted")));
                predicates.add(cb.equal(root.get("status"), UserStatus.valueOf(statusFilter)));
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdBefore != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), createdBefore));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        List<AdminUserDetailDTO> usersContent = users.getContent().stream()
                .map(user -> toAdminDetail(AdminUserDetailDTO.from(user), user))
                .toList();

        return new PaginatedResponse<>(
                usersContent,
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages(),
                users.isLast()
        );
    }

    @Transactional(readOnly = true)
    public AdminUserDetailDTO getUserByIdForAdmin(String userId) {
        UserEntity user = findUserOrThrow(parseUserId(userId));

        AdminUserDetailDTO.Stats stats = new AdminUserDetailDTO.Stats(
                postCrudRepository.countByUserIdAndIsDeletedFalse(user.getId()),
                commentRepository.countByUserId(user.getId()),
                followRepository.countByFollowedId(user.getId()),
                followRepository.countByFollowerId(user.getId())
        );
        return toAdminDetail(AdminUserDetailDTO.from(user, stats), user);
    }

    @Transactional
    public AdminUserDetailDTO updateUserForAdmin(String userId, AdminUpdateUserRequest request, UUID adminId) {
        UserEntity user = findUserOrThrow(parseUserId(userId));

        Role previousRole = user.getRole();
        boolean roleChanges = request.role() != null && request.role() != previousRole;
        if (roleChanges) {
            if (user.getId().equals(adminId)) {
                throw new BusinessException(GlobalMessage.CANNOT_CHANGE_OWN_ROLE, HttpStatus.FORBIDDEN);
            }
            // defense in depth: never leave the system without an active admin
            if (previousRole == Role.ADMIN && !user.isDeleted()
                    && userRepository.countByRoleAndIsDeletedFalse(Role.ADMIN) <= 1) {
                throw new BusinessException(GlobalMessage.LAST_ADMIN, HttpStatus.CONFLICT);
            }
        }

        applyCommonUserUpdates(user, request.username(), request.email(), request.firstName(),
                request.lastName(), request.phoneNumber(), request.profileImage());

        if (roleChanges) {
            user.setRole(request.role());
        }

        UserEntity savedUser = userRepository.save(user);
        auditLogService.record(AdminAuditAction.USER_UPDATE, AuditTargetType.USER, savedUser.getId().toString(),
                null, AuditLogService.metadata(
                        "fields", changedFields(request),
                        "previousRole", roleChanges ? previousRole : null,
                        "newRole", roleChanges ? savedUser.getRole() : null));

        if (savedUser.isDeleted() || savedUser.getStatus() == UserStatus.BANNED) {
            userSearchService.deleteUser(savedUser.getId().toString());
        } else {
            userSearchService.indexUser(savedUser);
        }

        return toAdminDetail(AdminUserDetailDTO.from(savedUser), savedUser);
    }

    @Transactional
    public AdminUserDetailDTO banUserForAdmin(String userId, BanUserRequest request, UUID adminId) {
        UserEntity user = findUserOrThrow(parseUserId(userId));

        if (user.getId().equals(adminId)) {
            throw new BusinessException(GlobalMessage.CANNOT_BAN_SELF, HttpStatus.FORBIDDEN);
        }
        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException(GlobalMessage.CANNOT_BAN_ADMIN, HttpStatus.FORBIDDEN);
        }
        if (user.isDeleted()) {
            throw new BusinessException(GlobalMessage.USER_ALREADY_DELETED, HttpStatus.CONFLICT);
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(GlobalMessage.USER_ALREADY_BANNED, HttpStatus.CONFLICT);
        }

        String reason = request.reason().trim();
        user.setStatus(UserStatus.BANNED);
        user.setBannedAt(Instant.now());
        user.setBannedBy(adminId);
        user.setBanReason(reason);
        UserEntity savedUser = userRepository.save(user);

        reportClosingService.closeOpenReports(ReportTargetType.USER, savedUser.getId(), adminId, ReportAction.BAN);
        auditLogService.record(AdminAuditAction.USER_BAN, AuditTargetType.USER, savedUser.getId().toString(),
                reason, null);

        // refresh tokens are kept: refresh and every JWT request reject a banned user and tell them why.
        // They are revoked on unban, so a session from before the ban never silently comes back.
        userSearchService.deleteUser(savedUser.getId().toString());

        return toAdminDetail(AdminUserDetailDTO.from(savedUser), savedUser);
    }

    @Transactional
    public AdminUserDetailDTO unbanUserForAdmin(String userId, UnbanUserRequest request) {
        UserEntity user = findUserOrThrow(parseUserId(userId));

        if (user.getStatus() != UserStatus.BANNED) {
            throw new BusinessException(GlobalMessage.USER_NOT_BANNED, HttpStatus.CONFLICT);
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setBannedAt(null);
        user.setBannedBy(null);
        user.setBanReason(null);
        UserEntity savedUser = userRepository.save(user);

        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? null : request.reason().trim();
        auditLogService.record(AdminAuditAction.USER_UNBAN, AuditTargetType.USER, savedUser.getId().toString(),
                reason, null);

        authService.revokeRefreshTokens(savedUser.getId());
        // a user that deleted the account while banned must not come back into search
        if (!savedUser.isDeleted()) {
            userSearchService.indexUser(savedUser);
        }

        return toAdminDetail(AdminUserDetailDTO.from(savedUser), savedUser);
    }

    @Transactional
    public Operation forceLogoutForAdmin(String userId) {
        UserEntity user = findUserOrThrow(parseUserId(userId));

        authService.revokeRefreshTokens(user.getId());
        auditLogService.record(AdminAuditAction.USER_FORCE_LOGOUT, AuditTargetType.USER, user.getId().toString(),
                null, null);

        return Operation.EXECUTED;
    }

    private UserEntity findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }

    // names only, never values: the audit log must not become a second copy of user PII
    private List<String> changedFields(AdminUpdateUserRequest request) {
        List<String> fields = new ArrayList<>();
        if (request.username() != null && !request.username().isBlank()) fields.add("username");
        if (request.email() != null && !request.email().isBlank()) fields.add("email");
        if (request.firstName() != null) fields.add("firstName");
        if (request.lastName() != null) fields.add("lastName");
        if (request.phoneNumber() != null) fields.add("phoneNumber");
        if (request.profileImage() != null) fields.add("profileImage");
        if (request.role() != null) fields.add("role");
        return fields;
    }

    private UserEntity findActiveUserOrThrow(UUID currentUserId) {
        return userRepository.findActiveById(currentUserId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));
    }


    private UserDetailDTO toUserDetail(UserEntity user) {
        return UserDetailDTO.from(user).withProfileImageUrl(profileImageUrlResolver.resolve(user.getProfileImage()));
    }

    private AdminUserDetailDTO toAdminDetail(AdminUserDetailDTO dto, UserEntity user) {
        return dto.withProfileImageUrl(profileImageUrlResolver.resolve(user.getProfileImage()));
    }

    private void applyCommonUserUpdates(
            UserEntity user,
            String username,
            String email,
            String firstName,
            String lastName,
            String phoneNumber,
            String profileImage
    ) {
        if (username != null && !username.isBlank()) {
            validateUsernameIsAvailable(username, user.getId());
            user.setUsername(username);
        }

        if (email != null && !email.isBlank()) {
            validateEmailIsAvailable(email, user.getId());
            user.setEmail(email);
        }

        if (firstName != null) {
            user.setFirstName(firstName);
        }

        if (lastName != null) {
            user.setLastName(lastName);
        }

        if (phoneNumber != null) {
            user.setPhoneNumber(phoneNumber);
        }

        if (profileImage != null) {
            user.setProfileImage(profileImage);
        }
    }

    private void validateUsernameIsAvailable(String username, UUID currentUserId) {
        if (userRepository.existsByUsernameAndIdNot(username, currentUserId)) {
            throw new BusinessException(GlobalMessage.USERNAME_ALREADY_TAKEN, HttpStatus.BAD_REQUEST);
        }
    }

    private void validateEmailIsAvailable(String email, UUID currentUserId) {
        if (userRepository.existsByEmailAndIdNot(email, currentUserId)) {
            throw new BusinessException(GlobalMessage.EMAIL_ALREADY_TAKEN, HttpStatus.BAD_REQUEST);
        }
    }

    private UUID parseUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(GlobalMessage.BAD_REQUEST, HttpStatus.BAD_REQUEST);
        }
    }

}
