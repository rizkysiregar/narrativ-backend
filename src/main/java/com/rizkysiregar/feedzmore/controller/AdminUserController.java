package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.AdminUserDetailDTO;
import com.rizkysiregar.feedzmore.model.dto.UserDetailDTO;
import com.rizkysiregar.feedzmore.model.request.AdminUpdateUserRequest;
import com.rizkysiregar.feedzmore.model.request.UserSearchRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserManagementService userManagementService;

    @Operation(summary = "Admin search/list users")
    @GetMapping
    public PaginatedResponse<AdminUserDetailDTO> searchUsers(
            @RequestParam(required = false) String username,
            @Parameter(hidden = true) Pageable pageable
    ) {
        return userManagementService.searchUsersForAdmin(
                new UserSearchRequest(username),
                pageable
        );
    }

    @Operation(summary = "Admin get user detail by userId")
    @GetMapping("{userId}")
    public AdminUserDetailDTO getUserById(
            @PathVariable @NotBlank String userId
    ) {
        return userManagementService.getUserByIdForAdmin(userId);
    }

    @Operation(summary = "Admin update user data")
    @PatchMapping("{userId}")
    public AdminUserDetailDTO updateUser(
            @PathVariable @NotBlank String userId,
            @Valid @RequestBody AdminUpdateUserRequest request
    ) {
        return userManagementService.updateUserForAdmin(userId, request);
    }

    @Operation(summary = "Admin soft delete user")
    @DeleteMapping("{userId}")
    public com.rizkysiregar.feedzmore.util.Operation softDeleteUser(
            @PathVariable @NotBlank String userId
    ) {
        return userManagementService.softDeleteUserForAdmin(userId);
    }

    @Operation(summary = "Admin restore soft-deleted user")
    @PatchMapping("{userId}/restore")
    public com.rizkysiregar.feedzmore.util.Operation restoreUser(
            @PathVariable @NotBlank String userId
    ) {
        return userManagementService.restoreUserForAdmin(userId);
    }

}
