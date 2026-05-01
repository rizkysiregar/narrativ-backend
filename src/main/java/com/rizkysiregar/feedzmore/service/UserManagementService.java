package com.rizkysiregar.feedzmore.service;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.AdminUserDetailDTO;
import com.rizkysiregar.feedzmore.model.dto.UserDetailDTO;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.AdminUpdateUserRequest;
import com.rizkysiregar.feedzmore.model.request.UpdateMyUserRequest;
import com.rizkysiregar.feedzmore.model.request.UserSearchRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.util.Operation;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final UserSearchService userSearchService;

    public UserDetailDTO getMyProfile(UUID currentUserId) {
        UserEntity user = findActiveUserOrThrow(currentUserId);
        return UserDetailDTO.from(user);
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
                request.profileImage()
        );

        UserEntity savedUser = userRepository.save(user);
        userSearchService.indexUser(savedUser);

        return UserDetailDTO.from(savedUser);
    }

    @Transactional
    public Operation deleteMyAccount(UUID currentUserId) {
        UserEntity user = findActiveUserOrThrow(currentUserId);

        user.setDeleted(true);
        userRepository.save(user);
        userSearchService.deleteUser(user.getId().toString());

        return Operation.DELETED;
    }


    public PaginatedResponse<AdminUserDetailDTO> searchUsersForAdmin(UserSearchRequest request, Pageable pageable) {
        log.info("Admin search users request: {}", request);

        Page<UserEntity> users = userRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.username() != null && !request.username().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("username")),
                        "%" + request.username().toLowerCase() + "%"
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        List<AdminUserDetailDTO> usersContent = users.getContent().stream()
                .map(AdminUserDetailDTO::from)
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

    public AdminUserDetailDTO getUserByIdForAdmin(String userId) {
        UUID id = parseUserId(userId);

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));

        return AdminUserDetailDTO.from(user);
    }

    @Transactional
    public AdminUserDetailDTO updateUserForAdmin(String userId, AdminUpdateUserRequest request) {
        UUID id = parseUserId(userId);

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));

        applyCommonUserUpdates(user, request.username(), request.email(), request.firstName(),
                request.lastName(), request.phoneNumber(), request.profileImage());

        if (request.role() != null) {
            user.setRole(request.role());
        }

        UserEntity savedUser = userRepository.save(user);

        if (savedUser.isDeleted()) {
            userSearchService.deleteUser(savedUser.getId().toString());
        } else {
            userSearchService.indexUser(savedUser);
        }

        return AdminUserDetailDTO.from(savedUser);
    }

    @Transactional
    public Operation softDeleteUserForAdmin(String userId) {
        UUID id = parseUserId(userId);

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));

        if (user.isDeleted()) {
            return Operation.DELETED;
        }

        user.setDeleted(true);
        userRepository.save(user);
        userSearchService.deleteUser(user.getId().toString());

        return Operation.DELETED;
    }


    @Transactional
    public Operation restoreUserForAdmin(String userId) {
        UUID id = parseUserId(userId);

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));

        if (!user.isDeleted()) {
            return Operation.UPDATED;
        }

        user.setDeleted(false);
        UserEntity savedUser = userRepository.save(user);
        userSearchService.indexUser(savedUser);

        return Operation.UPDATED;
    }

    private UserEntity findActiveUserOrThrow(UUID currentUserId) {
        return userRepository.findByIdAndIsDeletedFalse(currentUserId)
                .orElseThrow(() -> new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND));
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
