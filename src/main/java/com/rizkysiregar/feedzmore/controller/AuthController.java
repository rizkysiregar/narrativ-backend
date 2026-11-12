package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.*;
import com.rizkysiregar.feedzmore.model.request.*;
import com.rizkysiregar.feedzmore.model.response.LoginResponse;
import com.rizkysiregar.feedzmore.service.AuthService;
import com.rizkysiregar.feedzmore.service.otp.IOtpService;
import com.rizkysiregar.feedzmore.util.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@RestController
@Slf4j
@Tag(name = "Authentication and User Related API's")
@Validated
@SecurityRequirement(name = "Bearer Authentication")
public class AuthController {

    final AuthService authService;
    private final IOtpService otpService;

    public AuthController(AuthService authService, IOtpService otpService) {
        this.authService = authService;
        this.otpService = otpService;
    }


    @io.swagger.v3.oas.annotations.Operation(summary = "Register a new user")
    @PostMapping("auth/register")
    public Operation register(@Valid @RequestBody RegisterUserRequest registerUserRequest) {
       log.info("REGISTER.USER username={}", registerUserRequest.getUsername());
        return authService.register(registerUserRequest, false);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Register a admin user")
    @PostMapping("auth/register-admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Operation registerAdmin(@Valid @RequestBody RegisterUserRequest registerUserRequest) {
        log.info("REGISTER.ADMIN username={}", registerUserRequest.getUsername());
        return authService.register(registerUserRequest, true);
    }

    @PostMapping("auth/admin/login")
    public LoginResponse adminLogin(@Valid @RequestBody UserLoginRequest userLoginRequest) {
        log.info("ADMIN LOGIN username={}", userLoginRequest.getUsername());
        return authService.adminLogin(userLoginRequest);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "User login", description = "use this to gain jwt credentials for user validity")
    @PostMapping("auth/login")
    public LoginResponse login(@Valid @RequestBody UserLoginRequest userLoginRequest) {
        log.info("LOGIN username={}", userLoginRequest.getUsername());
        return authService.login(userLoginRequest);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Login with Google Account- Token ID")
    @PostMapping("auth/google")
    public LoginResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.googleLogin(request);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Refresh access token", description = "Exchange a valid refresh token for a new access token and a new (rotated) refresh token")
    @PostMapping("auth/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refreshToken(request.getRefreshToken());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "User validation and OTP Request", description = "Use this for sending otp into user registered email")
    @PostMapping("auth/forgot-password")
    public SentOtpDTO forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        return otpService.sendOtp(request.getEmail());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Verify OTP", description = "Validate is OTP matched or not")
    @PostMapping("auth/verify-otp")
    public ResetTokenDTO verifyOtp(@RequestBody @Valid VerifyOtpRequest request) {
        return otpService.verifyOtp(request.getEmail(), request.getOtp());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "New password", description = "Take email and new password also resetToken is needed")
    @PostMapping("auth/reset-password")
    public Operation confirmResetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        return authService.confirmResetPassword(resetPasswordRequest);
    }

}
