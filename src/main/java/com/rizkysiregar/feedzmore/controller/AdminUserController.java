package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.dto.AdminUserDetailDTO;
import com.rizkysiregar.feedzmore.model.request.AdminUpdateUserRequest;
import com.rizkysiregar.feedzmore.model.request.AdminUserSearchRequest;
import com.rizkysiregar.feedzmore.model.request.BanUserRequest;
import com.rizkysiregar.feedzmore.model.request.UnbanUserRequest;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserManagementService userManagementService;

    @Operation(summary = "Admin search/list users",
            description = "status: ACTIVE, BANNED or DELETED (deleted by the user). createdFrom/createdTo are inclusive dates (yyyy-MM-dd) in Asia/Jakarta time.")
    @GetMapping
    public PaginatedResponse<AdminUserDetailDTO> searchUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @Parameter(hidden = true)
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return userManagementService.searchUsersForAdmin(
                new AdminUserSearchRequest(username, email, role, status, createdFrom, createdTo),
                pageable
        );
    }

    @Operation(summary = "Admin get user detail by userId, including post/comment/follow counts")
    @GetMapping("{userId}")
    public AdminUserDetailDTO getUserById(
            @PathVariable @NotBlank String userId
    ) {
        return userManagementService.getUserByIdForAdmin(userId);
    }

    @Operation(summary = "Admin update user data")
    @PatchMapping("{userId}")
    public AdminUserDetailDTO updateUser(
            @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable @NotBlank String userId,
            @Valid @RequestBody AdminUpdateUserRequest request
    ) {
        return userManagementService.updateUserForAdmin(userId, request, admin.getUserId());
    }

    @Operation(summary = "Admin ban user",
            description = "Permanent until unbanned. The user cannot log in, sessions are revoked and the content is hidden.")
    @PostMapping("{userId}/ban")
    public AdminUserDetailDTO banUser(
            @AuthenticationPrincipal UserPrincipal admin,
            @PathVariable @NotBlank String userId,
            @Valid @RequestBody BanUserRequest request
    ) {
        return userManagementService.banUserForAdmin(userId, request, admin.getUserId());
    }

    @Operation(summary = "Admin unban user")
    @PostMapping("{userId}/unban")
    public AdminUserDetailDTO unbanUser(
            @PathVariable @NotBlank String userId,
            @Valid @RequestBody(required = false) UnbanUserRequest request
    ) {
        return userManagementService.unbanUserForAdmin(userId, request);
    }

    @Operation(summary = "Admin force logout user",
            description = "Revokes the refresh tokens of the user. An access token that was already issued stays valid until it expires (15 minutes).")
    @PostMapping("{userId}/force-logout")
    public com.rizkysiregar.feedzmore.util.Operation forceLogout(
            @PathVariable @NotBlank String userId
    ) {
        return userManagementService.forceLogoutForAdmin(userId);
    }

}
