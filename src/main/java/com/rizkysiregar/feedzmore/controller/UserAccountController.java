package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.UserPrincipal;
import com.rizkysiregar.feedzmore.model.dto.UserDetailDTO;
import com.rizkysiregar.feedzmore.model.request.UpdateMyUserRequest;
import com.rizkysiregar.feedzmore.model.request.CreateProfileImageUploadUrlRequest;
import com.rizkysiregar.feedzmore.model.request.SetProfileImageRequest;
import com.rizkysiregar.feedzmore.model.response.ProfileImageUploadUrlResponse;
import com.rizkysiregar.feedzmore.service.ProfileImageService;
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
    private final ProfileImageService profileImageService;

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

    @Operation(summary = "Get a presigned PUT url to upload a new profile picture (jpeg, png or webp, max 2 MB)")
    @PostMapping("/profile-image/upload-url")
    public ProfileImageUploadUrlResponse createProfileImageUploadUrl(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateProfileImageUploadUrlRequest request
    ) {
        return profileImageService.createUploadUrl(currentUser.getUserId(), request);
    }

    @Operation(summary = "Use an uploaded object as the profile picture; replaces and deletes the previous one")
    @PutMapping("/profile-image")
    public UserDetailDTO setProfileImage(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody SetProfileImageRequest request
    ) {
        return profileImageService.setProfileImage(currentUser.getUserId(), request);
    }

    @Operation(summary = "Remove the profile picture")
    @DeleteMapping("/profile-image")
    public UserDetailDTO deleteProfileImage(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return profileImageService.deleteProfileImage(currentUser.getUserId());
    }

    @Operation(summary = "Soft delete current logged-in user account")
    @DeleteMapping
    public com.rizkysiregar.feedzmore.util.Operation deleteMyAccount(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return userManagementService.deleteMyAccount(currentUser.getUserId());
    }


}
