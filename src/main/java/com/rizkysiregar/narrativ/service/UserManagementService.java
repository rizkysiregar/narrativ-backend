package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.AdminUserDetailDTO;
import com.rizkysiregar.narrativ.model.dto.UserDetailDTO;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.AdminAuditAction;
import com.rizkysiregar.narrativ.model.enums.AuditTargetType;
import com.rizkysiregar.narrativ.model.enums.ReportAction;
import com.rizkysiregar.narrativ.model.enums.ReportTargetType;
import com.rizkysiregar.narrativ.model.enums.Role;
import com.rizkysiregar.narrativ.model.enums.UserStatus;
import com.rizkysiregar.narrativ.model.request.AdminUpdateUserRequest;
import com.rizkysiregar.narrativ.model.request.AdminUserSearchRequest;
import com.rizkysiregar.narrativ.model.request.BanUserRequest;
import com.rizkysiregar.narrativ.model.request.UnbanUserRequest;
import com.rizkysiregar.narrativ.model.request.UpdateMyUserRequest;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.model.request.DeleteAccountRequest;
import com.rizkysiregar.narrativ.repository.CommentRepository;
import com.rizkysiregar.narrativ.repository.LikeRepository;
import com.rizkysiregar.narrativ.repository.PostMediaRepository;
import com.rizkysiregar.narrativ.repository.UserBlockRepository;
import com.rizkysiregar.narrativ.repository.FollowRepository;
import com.rizkysiregar.narrativ.repository.PostCrudRepository;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.util.AdminDates;
import com.rizkysiregar.narrativ.util.AfterCommit;
import com.rizkysiregar.narrativ.util.EmailNormalizer;
import com.rizkysiregar.narrativ.util.Operation;
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
    private final AuditLogService auditLogService;
    private final AuthService authService;
    private final PostCrudRepository postCrudRepository;
    private final CommentRepository commentRepository;
    private final FollowRepository followRepository;
    private final ReportClosingService reportClosingService;
    private final ProfileImageUrlResolver profileImageUrlResolver;
    private final UserDeviceService userDeviceService;
    private final ProfileImageService profileImageService;
    private final LikeRepository likeRepository;
    private final UserBlockRepository userBlockRepository;
    private final PostMediaRepository postMediaRepository;
    private final MediaCleanupService mediaCleanupService;

    private static final String DELETED_COMMENT_TEXT = "[deleted]";

    public UserDetailDTO getMyProfile(UUID currentUserId) {
        UserEntity user = findActiveUserOrThrow(currentUserId);
        return toUserDetail(user);
    }

    @Transactional
    public UserDetailDTO updateMyProfile(UUID currentUserId, UpdateMyUserRequest request) {
        UserEntity user = findActiveUserOrThrow(currentUserId);

        // An unverified email change would skip the OTP of registration, and would let somebody park their account on
        // an address they do not own: a later Google sign-in with that address is linked to the existing account.
        // Sending the email it already has (a client that posts the whole form) is fine.
        if (request.email() != null && !request.email().isBlank()
                && !EmailNormalizer.normalize(request.email()).equals(EmailNormalizer.normalize(user.getEmail()))) {
            throw new BusinessException(GlobalMessage.EMAIL_CHANGE_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
        }

        applyCommonUserUpdates(
                user,
                request.username(),
                null,
                request.firstName(),
                request.lastName(),
                request.phoneNumber(),
                null // the picture only changes through ProfileImageService
        );

        UserEntity savedUser = userRepository.save(user);

        return toUserDetail(savedUser);
    }

    /**
     * Deletes the account for good from the user's side: nothing that identifies them stays. The row is kept (posts,
     * reports and audit entries point at it) but its email, username, name, phone, password and Google link are
     * replaced, so the email and username can be registered again. Sessions and devices are cut, the posts are
     * deleted together with their media, comments lose their text, likes and follows are removed.
     */
    @Transactional
    public Operation deleteMyAccount(UUID currentUserId, DeleteAccountRequest request) {
        UserEntity user = findActiveUserOrThrow(currentUserId);

        if (user.getRole() == Role.ADMIN) {
            throw new BusinessException(GlobalMessage.CANNOT_DELETE_ADMIN, HttpStatus.FORBIDDEN);
        }
        if (!authService.confirmsIdentity(user, request.password(), request.idToken())) {
            throw new BusinessException(GlobalMessage.CONFIRMATION_FAILED, HttpStatus.FORBIDDEN);
        }

        // cut the sessions first: whatever fails later, nobody can keep using the account
        authService.revokeRefreshTokens(currentUserId);
        userDeviceService.removeAllDevices(currentUserId);

        profileImageService.deleteProfileImage(currentUserId);
        deleteOwnPosts(currentUserId);
        commentRepository.overwriteContentByUserId(currentUserId, DELETED_COMMENT_TEXT);
        likeRepository.deleteAllByUserId(currentUserId);
        followRepository.deleteAllByUserId(currentUserId);
        userBlockRepository.deleteAllByUserId(currentUserId);

        String tag = currentUserId.toString().replace("-", "");
        user.setUsername("del_" + tag.substring(0, 12));
        user.setEmail("deleted-" + tag + "@deleted.invalid");
        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setPhoneNumber(null);
        user.setGoogleId(null);
        user.setProfileImage(null);
        user.setPassword(authService.unusablePasswordHash());
        user.setDeleted(true);
        userRepository.save(user);

        log.info("USER.ACCOUNT.DELETED userId={}", currentUserId);
        return Operation.DELETED;
    }

    /**
     * Every post of the user is deleted in a few statements, however many there are. The files go afterwards in
     * the background: the rows that point at them are already gone, so a file that is not removed is an orphan
     * that the cleanup job finds, never a leftover that stays linked to the account.
     */
    private void deleteOwnPosts(UUID userId) {
        List<String> keys = new ArrayList<>(postMediaRepository.findMediaKeysOfLivePosts(userId));
        keys.addAll(postMediaRepository.findThumbnailKeysOfLivePosts(userId));

        postMediaRepository.deleteAllOfLivePosts(userId);
        postCrudRepository.softDeleteAllByUserId(userId);

        if (!keys.isEmpty()) {
            AfterCommit.run("delete media of account " + userId, () -> mediaCleanupService.deleteObjects(keys));
        }
    }

    public PaginatedResponse<AdminUserDetailDTO> searchUsersForAdmin(AdminUserSearchRequest request, Pageable pageable) {
        log.debug("ADMIN.USER.SEARCH request={}", request);

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
            String normalized = EmailNormalizer.normalize(email);
            validateEmailIsAvailable(normalized, user.getId());
            user.setEmail(normalized);
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
