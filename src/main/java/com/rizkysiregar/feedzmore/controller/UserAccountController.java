package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.dto.UserDetailDTO;
import com.rizkysiregar.feedzmore.model.request.UpdateMyUserRequest;
import com.rizkysiregar.feedzmore.service.UserManagementService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me")
public class UserAccountController {

    private final UserManagementService userManagementService;

    @Operation(summary = "Get current logged-in user profile")
    @GetMapping
    public UserDetailDTO getMyProfile(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return userManagementService.getMyProfile(currentUser.getUserId());
    }

    @Operation(summary = "Update current logged-in user profile")
    @PatchMapping
    public UserDetailDTO updateMyProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdateMyUserRequest request
    ) {
        return userManagementService.updateMyProfile(currentUser.getUserId(), request);
    }

    @Operation(summary = "Soft delete current logged-in user account")
    @DeleteMapping
    public com.rizkysiregar.feedzmore.util.Operation deleteMyAccount(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return userManagementService.deleteMyAccount(currentUser.getUserId());
    }


}
