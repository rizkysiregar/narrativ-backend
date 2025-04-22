package com.rizkysiregar.feedzmore.controller;

import com.rizkysiregar.feedzmore.model.dto.ResetTokenDTO;
import com.rizkysiregar.feedzmore.model.dto.SentOtpDTO;
import com.rizkysiregar.feedzmore.model.dto.UserLoginDTO;
import com.rizkysiregar.feedzmore.model.dto.UserRegisterDto;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.model.request.ResetPasswordRequest;
import com.rizkysiregar.feedzmore.model.request.UserLoginRequest;
import com.rizkysiregar.feedzmore.model.request.VerifyOtpRequest;
import com.rizkysiregar.feedzmore.service.UsersService;
import com.rizkysiregar.feedzmore.service.otp.IOtpService;
import com.rizkysiregar.feedzmore.util.Operation;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@Tag(name = "Users API")
@Validated
@SecurityRequirement(name = "Bearer Authentication")
public class UsersController {

    @Autowired
    UsersService usersService;

    @Autowired
    private IOtpService otpService;


    @ApiOperation(value = "Register a new user", notes = "use this to register a new user")
    @PostMapping("/register")
    public UserRegisterDto register(@Valid @RequestBody UserEntity userEntity) {
        log.info("::. Entering /register users");
        return usersService.register(userEntity);
    }

    @ApiOperation(value = "User login", notes = "use this to gain jwt credentials for user validity")
    @PostMapping("/login")
    public UserLoginDTO login(@RequestBody UserLoginRequest userLoginRequest) {
        log.info("::. Entering /login users");
        log.info(":: " + userLoginRequest.toString());
        return usersService.verify(userLoginRequest);
    }

    @ApiOperation(value = "User validation and OTP Request", notes = "Use this for sending otp into user registered email")
    @PostMapping("/forgot-password")
    public SentOtpDTO forgotPassword(@RequestParam @NotBlank @Email String email) {
        return otpService.sendOtp(email);
    }

    @ApiOperation(value = "Verify OTP", notes = "Validate is OTP matched or not")
    @PostMapping("/verify-otp")
    public ResetTokenDTO verifyOtp(@RequestBody @Valid VerifyOtpRequest request) {
        return otpService.verifyOtp(request.getEmail(), request.getOtp());
    }

    @ApiOperation(value = "New password", notes = "Take email and new password also resetToken is needed")
    @PostMapping("/reset-password")
    public Operation resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        return usersService.resetPassword(resetPasswordRequest);
    }
}
