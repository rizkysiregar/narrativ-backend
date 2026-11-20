package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.UserPrincipal;
import com.rizkysiregar.narrativ.model.dto.SentOtpDTO;
import com.rizkysiregar.narrativ.model.dto.UserDetailDTO;
import com.rizkysiregar.narrativ.model.request.ChangeEmailOtpRequest;
import com.rizkysiregar.narrativ.model.request.ConfirmEmailChangeRequest;
import com.rizkysiregar.narrativ.model.request.DeleteAccountRequest;
import com.rizkysiregar.narrativ.model.request.UpdateMyUserRequest;
import com.rizkysiregar.narrativ.model.request.CreateProfileImageUploadUrlRequest;
import com.rizkysiregar.narrativ.model.request.SetProfileImageRequest;
import com.rizkysiregar.narrativ.model.response.LoginResponse;
import com.rizkysiregar.narrativ.model.response.ProfileImageUploadUrlResponse;
import com.rizkysiregar.narrativ.service.ProfileImageService;
import com.rizkysiregar.narrativ.service.UserManagementService;
import com.rizkysiregar.narrativ.service.auth.WebSessionCookie;
import com.rizkysiregar.narrativ.service.otp.EmailChangeService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final EmailChangeService emailChangeService;
    private final WebSessionCookie webSessionCookie;

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

    @Operation(summary = "Delete the logged-in user's account and personal data",
            description = "Needs the current password, or a fresh Google id token for an account that signs in with Google. Cannot be undone")
    @PostMapping("/deletion")
    public com.rizkysiregar.narrativ.util.Operation deleteMyAccount(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody DeleteAccountRequest request
    ) {
        return userManagementService.deleteMyAccount(currentUser.getUserId(), request);
    }

    @Operation(summary = "Send an OTP to the new email of the account",
            description = "Step 1 of an email change. 400 when it is the current email or another account has it, 429 within a minute of the previous OTP")
    @PostMapping("/email/otp")
    public SentOtpDTO sendEmailChangeOtp(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody ChangeEmailOtpRequest request
    ) {
        return emailChangeService.sendOtp(currentUser.getUserId(), request.email());
    }

    @Operation(summary = "Change the email of the account",
            description = "Step 2: the OTP sent to the new email plus the current password, or a fresh Google id token for an account that signs in with Google. Every other session is ended; the answer is a new token pair for this device (a web client gets the refresh token as an HttpOnly cookie)")
    @PostMapping("/email")
    public LoginResponse confirmEmailChange(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody ConfirmEmailChangeRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return webSessionCookie.deliver(emailChangeService.confirm(currentUser.getUserId(), request), httpRequest, httpResponse);
    }
}
