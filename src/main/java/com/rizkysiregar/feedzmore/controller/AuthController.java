package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.*;
import com.rizkysiregar.feedzmore.model.request.*;
import com.rizkysiregar.feedzmore.model.response.PaginatedResponse;
import com.rizkysiregar.feedzmore.service.AuthService;
import com.rizkysiregar.feedzmore.service.otp.IOtpService;
import com.rizkysiregar.feedzmore.util.Operation;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
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


    @ApiOperation(value = "Register a new user", notes = "use this to register a new user")
    @PostMapping("auth/register")
    public Operation register(@Valid @RequestBody RegisterUserRequest registerUserRequest) {
       log.info("REGISTER.USER {}", registerUserRequest);
        return authService.register(registerUserRequest);
    }

    @ApiOperation(value = "User login", notes = "use this to gain jwt credentials for user validity")
    @PostMapping("auth/login")
    public UserLoginDTO login(@Valid @RequestBody UserLoginRequest userLoginRequest) {
        log.info("LOGIN {} ", userLoginRequest);
        return authService.login(userLoginRequest);
    }

    @ApiOperation(value = "Login with Google Account- Token ID")
    @PostMapping("auth/google")
    public UserLoginDTO googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return authService.googleLogin(request);
    }

    @ApiOperation(value = "User validation and OTP Request", notes = "Use this for sending otp into user registered email")
    @PostMapping("auth/forgot-password")
    public SentOtpDTO forgotPassword(@RequestBody @Valid EmailDTO emailDTO) {
        return otpService.sendOtp(emailDTO.getEmail());
    }

    @ApiOperation(value = "Verify OTP", notes = "Validate is OTP matched or not")
    @PostMapping("auth/verify-otp")
    public ResetTokenDTO verifyOtp(@RequestBody @Valid VerifyOtpRequest request) {
        return otpService.verifyOtp(request.getEmail(), request.getOtp());
    }

    @ApiOperation(value = "New password", notes = "Take email and new password also resetToken is needed")
    @PostMapping("auth/reset-password")
    public Operation confirmResetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        return authService.confirmResetPassword(resetPasswordRequest);
    }

    @ApiOperation(value = "Getting user details data by userId", notes = "pass userId that you got from login response")
    @GetMapping("users/{userId}")
    public UserDetailDTO getUserById(@PathVariable @NotBlank String userId) {
        return authService.getUserById(userId);
    }


    @ApiOperation(
            value = "Search users by it's username"
    )
    @GetMapping("users")
    public PaginatedResponse<UserDetailDTO> searchUsers(
            @RequestParam(required = false) @NotBlank  String username,
            @Parameter(hidden = true) Pageable pageable
    ) {
        return authService.search(
                new UserSearchRequest(username),
                pageable
        );
    }

}
