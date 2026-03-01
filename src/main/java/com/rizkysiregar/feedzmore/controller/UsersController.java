package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.*;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.*;
import com.rizkysiregar.feedzmore.service.UsersService;
import com.rizkysiregar.feedzmore.service.otp.IOtpService;
import com.rizkysiregar.feedzmore.util.Operation;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@Tag(name = "Users API")
@Validated
@SecurityRequirement(name = "Bearer Authentication")
public class UsersController {

    final UsersService usersService;
    private final IOtpService otpService;

    public UsersController(UsersService usersService, IOtpService otpService) {
        this.usersService = usersService;
        this.otpService = otpService;
    }


    @ApiOperation(value = "Register a new user", notes = "use this to register a new user")
    @PostMapping("/auth/register")
    public Operation register(@Valid @RequestBody RegisterUserRequest registerUserRequest) {
        log.info("::. Entering /register users");
        return usersService.register(registerUserRequest);
    }

    @ApiOperation(value = "User login", notes = "use this to gain jwt credentials for user validity")
    @PostMapping("/auth/login")
    public UserLoginDTO login(@RequestBody UserLoginRequest userLoginRequest) {
        log.info("::. Entering /login users");
        log.info(":: " + userLoginRequest.toString());
        return usersService.verify(userLoginRequest);
    }

    @ApiOperation(value = "User validation and OTP Request", notes = "Use this for sending otp into user registered email")
    @PostMapping("/auth/password/forgot")
    public SentOtpDTO forgotPassword(@RequestBody @Valid EmailDTO emailDTO) {
        return otpService.sendOtp(emailDTO.getEmail());
    }

    @ApiOperation(value = "Verify OTP", notes = "Validate is OTP matched or not")
    @PostMapping("/auth/password/verify-otp")
    public ResetTokenDTO verifyOtp(@RequestBody @Valid VerifyOtpRequest request) {
        return otpService.verifyOtp(request.getEmail(), request.getOtp());
    }

    @ApiOperation(value = "New password", notes = "Take email and new password also resetToken is needed")
    @PostMapping("/auth/password/reset")
    public Operation confirmResetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        return usersService.confirmResetPassword(resetPasswordRequest);
    }

    @ApiOperation(value = "Getting user details data by userId", notes = "pass userId that you got from login response")
    @GetMapping("/user/{userId}")
    public UserDetailDTO getUserById(@PathVariable String userId) {
        return usersService.getUserById(userId);
    }


    @ApiOperation(value = "Getting registered user base on some data that its have", notes = "the argument is dynamic, can be change base on requirement")
    @GetMapping("/users")
    public Page<UserDetailDTO> searchUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String name,
            @Parameter(hidden = true) Pageable pageable
    ) {
        return usersService.search(
                new UserSearchRequest(username, name),
                pageable
        );
    }

}
