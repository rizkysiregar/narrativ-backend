package com.rizkysiregar.narrativ.controller;

import com.rizkysiregar.narrativ.model.dto.*;
import com.rizkysiregar.narrativ.model.request.*;
import com.rizkysiregar.narrativ.model.response.LoginResponse;
import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.service.AuthService;
import com.rizkysiregar.narrativ.service.auth.WebSessionCookie;
import com.rizkysiregar.narrativ.service.otp.IOtpService;
import com.rizkysiregar.narrativ.service.otp.IRegistrationOtpService;
import com.rizkysiregar.narrativ.util.EmailNormalizer;
import com.rizkysiregar.narrativ.util.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@RestController
@Slf4j
@Tag(name = "Authentication and User Related API's")
@Validated
@SecurityRequirement(name = "Bearer Authentication")
public class AuthController {

    static final String WEB_SESSION_NOTE = "A web client (X-Client-Platform: web) gets the refresh token as an HttpOnly cookie instead of the refreshToken field";

    final AuthService authService;
    private final IOtpService otpService;
    private final IRegistrationOtpService registrationOtpService;
    private final WebSessionCookie webSessionCookie;

    public AuthController(AuthService authService, IOtpService otpService,
                          IRegistrationOtpService registrationOtpService, WebSessionCookie webSessionCookie) {
        this.authService = authService;
        this.otpService = otpService;
        this.registrationOtpService = registrationOtpService;
        this.webSessionCookie = webSessionCookie;
    }


    @io.swagger.v3.oas.annotations.Operation(summary = "Register a new user")
    @PostMapping("auth/register")
    public Operation register(@Valid @RequestBody RegisterUserRequest registerUserRequest) {
       log.debug("AUTH.REGISTER.USER username={}", registerUserRequest.getUsername());
        return authService.register(registerUserRequest, false);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Send the registration OTP", description = "Sends an OTP to the email that is about to be registered. verificationRequired=false means this environment does not verify emails: skip the OTP step and register directly")
    @PostMapping("auth/register/otp")
    public RegisterOtpDTO registerOtp(@RequestBody @Valid RegisterOtpRequest request) {
        return registrationOtpService.sendOtp(EmailNormalizer.normalize(request.getEmail()));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Verify the registration OTP", description = "Trades the right OTP for a registrationToken, which POST /auth/register asks for")
    @PostMapping("auth/register/verify-otp")
    public RegistrationTokenDTO registerVerifyOtp(@RequestBody @Valid VerifyOtpRequest request) {
        return registrationOtpService.verifyOtp(EmailNormalizer.normalize(request.getEmail()), request.getOtp());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Register a admin user")
    @PostMapping("auth/register-admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Operation registerAdmin(@Valid @RequestBody RegisterUserRequest registerUserRequest) {
        log.debug("AUTH.REGISTER.ADMIN username={}", registerUserRequest.getUsername());
        return authService.register(registerUserRequest, true);
    }

    @PostMapping("auth/admin/login")
    public LoginResponse adminLogin(@Valid @RequestBody UserLoginRequest userLoginRequest) {
        log.debug("AUTH.LOGIN.ADMIN username={}", userLoginRequest.getUsername());
        return authService.adminLogin(userLoginRequest);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "User login", description = "use this to gain jwt credentials for user validity. " + WEB_SESSION_NOTE)
    @PostMapping("auth/login")
    public LoginResponse login(@Valid @RequestBody UserLoginRequest userLoginRequest,
                               HttpServletRequest request, HttpServletResponse response) {
        log.debug("AUTH.LOGIN username={}", userLoginRequest.getUsername());
        return webSessionCookie.deliver(authService.login(userLoginRequest), request, response);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Login with Google Account- Token ID", description = WEB_SESSION_NOTE)
    @PostMapping("auth/google")
    public LoginResponse googleLogin(@Valid @RequestBody GoogleLoginRequest googleLoginRequest,
                                     HttpServletRequest request, HttpServletResponse response) {
        return webSessionCookie.deliver(authService.googleLogin(googleLoginRequest), request, response);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Refresh access token", description = "Exchange a valid refresh token for a new access token and a new (rotated) refresh token. A web client (X-Client-Platform: web) may send no body: the token is then read from the refresh cookie, and the rotated one is written back to it")
    @PostMapping("auth/refresh")
    public LoginResponse refresh(@RequestBody(required = false) RefreshTokenRequest refreshTokenRequest,
                                 HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = webSessionCookie.refreshToken(bodyToken(refreshTokenRequest), request);
        if (refreshToken == null) {
            throw new BusinessException(GlobalMessage.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
        }
        return webSessionCookie.deliver(authService.refreshToken(refreshToken), request, response);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Logout", description = "Revokes the given refresh token (for a web client: the one in the refresh cookie, which is then cleared). Always succeeds, also for an unknown, expired or missing token")
    @PostMapping("auth/logout")
    public Operation logout(@RequestBody(required = false) RefreshTokenRequest refreshTokenRequest,
                            HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = webSessionCookie.refreshToken(bodyToken(refreshTokenRequest), request);
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        webSessionCookie.clear(request, response);
        return Operation.DELETED;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "User validation and OTP Request", description = "Sends an OTP to the email when it has an account. The answer is the same for every email, registered or not, so it does not reveal which emails have an account")
    @PostMapping("auth/forgot-password")
    public SentOtpDTO forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        return otpService.sendOtp(EmailNormalizer.normalize(request.getEmail()));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Verify OTP", description = "Validate is OTP matched or not")
    @PostMapping("auth/verify-otp")
    public ResetTokenDTO verifyOtp(@RequestBody @Valid VerifyOtpRequest request) {
        return otpService.verifyOtp(EmailNormalizer.normalize(request.getEmail()), request.getOtp());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "New password", description = "Take email and new password also resetToken is needed")
    @PostMapping("auth/reset-password")
    public Operation confirmResetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        return authService.confirmResetPassword(resetPasswordRequest);
    }

    private static String bodyToken(RefreshTokenRequest refreshTokenRequest) {
        return refreshTokenRequest == null ? null : refreshTokenRequest.getRefreshToken();
    }
}
