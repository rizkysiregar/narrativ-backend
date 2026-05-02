package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.*;
import com.rizkysiregar.feedzmore.model.request.*;
import com.rizkysiregar.feedzmore.service.AuthService;
import com.rizkysiregar.feedzmore.service.otp.IOtpService;
import com.rizkysiregar.feedzmore.util.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
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
       log.info("REGISTER.USER {}", registerUserRequest);
        return authService.register(registerUserRequest, false);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Register a admin user")
    @PostMapping("auth/register-admin")
    public Operation registerAdmin(@Valid @RequestBody RegisterUserRequest registerUserRequest) {
        log.info("REGISTER.ADMIN {}", registerUserRequest);
        return authService.register(registerUserRequest, true);
    }

    @PostMapping("auth/admin/login")
    public UserLoginDTO adminLogin(@Valid @RequestBody UserLoginRequest userLoginRequest) {
        log.info("ADMIN LOGIN {}", userLoginRequest);
        return authService.adminLogin(userLoginRequest);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "User login", description = "use this to gain jwt credentials for user validity")
    @PostMapping("auth/login")
    public UserLoginDTO login(@Valid @RequestBody UserLoginRequest userLoginRequest) {
        log.info("LOGIN {} ", userLoginRequest);
        return authService.login(userLoginRequest);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Login with Google Account- Token ID")
    @PostMapping("auth/google")
    public UserLoginDTO googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.googleLogin(request);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "User validation and OTP Request", description = "Use this for sending otp into user registered email")
    @PostMapping("auth/forgot-password")
    public SentOtpDTO forgotPassword(@RequestBody @Valid EmailDTO emailDTO) {
        return otpService.sendOtp(emailDTO.getEmail());
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
