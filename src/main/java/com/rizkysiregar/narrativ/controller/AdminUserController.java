package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.dto.AdminUserDetailDTO;
import com.rizkysiregar.narrativ.model.request.AdminCreateUserRequest;
import com.rizkysiregar.narrativ.model.request.AdminUpdateUserRequest;
import com.rizkysiregar.narrativ.model.request.AdminUserSearchRequest;
import com.rizkysiregar.narrativ.model.request.BanUserRequest;
import com.rizkysiregar.narrativ.model.request.UnbanUserRequest;
import com.rizkysiregar.narrativ.model.response.PaginatedResponse;
import com.rizkysiregar.narrativ.service.UserManagementService;
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
            description = "status: ACTIVE, BANNED or DELETED (deleted by the user). createdFrom/createdTo are inclusive dates (yyyy-MM-dd) in Asia/Jakarta time. "
                    + "seeded: true for the accounts an admin created, false for the ones people signed up for")
    @GetMapping
    public PaginatedResponse<AdminUserDetailDTO> searchUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) Boolean seeded,
            @Parameter(hidden = true)
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return userManagementService.searchUsersForAdmin(
                new AdminUserSearchRequest(username, email, role, status, createdFrom, createdTo, seeded),
                pageable
        );
    }

    @Operation(summary = "Admin create a user account",
            description = "For filling the app with first content: a USER account with the sign-up rules but no email OTP, "
                    + "flagged seeded=true and audited (USER_CREATE). It signs in with the given username and password. "
                    + "400 USERNAME_ALREADY_TAKEN / EMAIL_ALREADY_TAKEN")
    @PostMapping
    public AdminUserDetailDTO createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return userManagementService.createUserForAdmin(request);
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
    public com.rizkysiregar.narrativ.util.Operation forceLogout(
            @PathVariable @NotBlank String userId
    ) {
        return userManagementService.forceLogoutForAdmin(userId);
    }

}
