package com.rizkysiregar.narrativ.service.otp;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.SentOtpDTO;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.model.enums.Role;
import com.rizkysiregar.narrativ.model.request.ConfirmEmailChangeRequest;
import com.rizkysiregar.narrativ.model.response.LoginResponse;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.AuthService;
import com.rizkysiregar.narrativ.service.email.IEmailService;
import com.rizkysiregar.narrativ.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailChangeServiceTest {

    private static final String NEW_EMAIL = "new@mail.com";

    @Mock private UserRepository userRepository;
    @Mock private AuthService authService;
    @Mock private IEmailService emailService;
    @Mock private RedisUtil redisUtil;

    private EmailChangeService service;
    private final UUID userId = UUID.randomUUID();
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new EmailChangeService(userRepository, authService, emailService, redisUtil);
        user = new UserEntity();
        user.setId(userId);
        user.setUsername("rizky");
        user.setEmail("old@mail.com");
        user.setRole(Role.USER);
        when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
    }

    private String otpKey() {
        return "email_change_otp:" + userId + ":" + NEW_EMAIL;
    }

    private void assertFails(GlobalMessage expected, HttpStatus status, Runnable action) {
        BusinessException e = assertThrows(BusinessException.class, action::run);
        assertEquals(expected.getMessage(), e.getMessage());
        assertEquals(status, e.getHttpStatus());
    }

    private ConfirmEmailChangeRequest confirmation(String otp) {
        return new ConfirmEmailChangeRequest(" New@Mail.com ", otp, "secret123", null);
    }

    @Test
    void sendOtp_mailsAnOtpToTheNewAddressBoundToTheUser() {
        when(redisUtil.setIfAbsent("email_change_otp_cooldown:" + userId, "1", 60)).thenReturn(true);
        when(emailService.sendOtpEmail(eq(NEW_EMAIL), anyString())).thenReturn(true);
        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);

        SentOtpDTO result = service.sendOtp(userId, "New@Mail.COM");

        verify(emailService).sendOtpEmail(eq(NEW_EMAIL), otp.capture());
        assertTrue(otp.getValue().matches("\\d{6}"));
        verify(redisUtil).set(otpKey(), otp.getValue(), 300L);
        assertEquals("n****w@mail.com", result.getOtpSendToEmailAddress());
    }

    @Test
    void sendOtp_refusesTheCurrentAndATakenAddress() {
        assertFails(GlobalMessage.EMAIL_UNCHANGED, HttpStatus.BAD_REQUEST, () -> service.sendOtp(userId, "OLD@mail.com"));

        when(userRepository.existsByEmailAndIdNot(NEW_EMAIL, userId)).thenReturn(true);
        assertFails(GlobalMessage.EMAIL_ALREADY_TAKEN, HttpStatus.BAD_REQUEST, () -> service.sendOtp(userId, NEW_EMAIL));

        verifyNoInteractions(emailService);
    }

    @Test
    void sendOtp_withinTheCooldownSendsNothing() {
        when(redisUtil.setIfAbsent("email_change_otp_cooldown:" + userId, "1", 60)).thenReturn(false);

        assertFails(GlobalMessage.OTP_RESEND_COOLDOWN, HttpStatus.TOO_MANY_REQUESTS, () -> service.sendOtp(userId, NEW_EMAIL));
        verifyNoInteractions(emailService);
    }

    @Test
    void confirm_changesTheEmailEndsOtherSessionsAndStartsANewOne() {
        when(authService.confirmsIdentity(user, "secret123", null)).thenReturn(true);
        when(redisUtil.get(otpKey())).thenReturn("123456");
        when(redisUtil.increment("email_change_otp_attempts:" + userId + ":" + NEW_EMAIL)).thenReturn(1L);
        LoginResponse session = LoginResponse.builder().token("access").refreshToken("refresh").build();
        when(authService.startSession(user)).thenReturn(session);

        LoginResponse result = service.confirm(userId, confirmation("123456"));

        assertSame(session, result);
        assertEquals(NEW_EMAIL, user.getEmail());
        var order = inOrder(userRepository, authService);
        order.verify(userRepository).save(user);
        order.verify(authService).revokeRefreshTokens(userId);
        order.verify(authService).startSession(user);
        // no transaction in a unit test, so the notice to the old address goes out right away
        ArgumentCaptor<java.util.Map<String, Object>> variables = ArgumentCaptor.forClass(java.util.Map.class);
        verify(emailService).sendEmailTemplate(eq("old@mail.com"), anyString(), eq("email-changed"), variables.capture());
        assertEquals("rizky", variables.getValue().get("username"));
        assertEquals("n****w@mail.com", variables.getValue().get("newEmail"));
        assertTrue(variables.getValue().get("changedAt").toString().endsWith("WIB"));
    }

    @Test
    void confirm_withoutTheRightPasswordDoesNotSpendTheOtp() {
        when(authService.confirmsIdentity(user, "secret123", null)).thenReturn(false);

        assertFails(GlobalMessage.CONFIRMATION_FAILED, HttpStatus.FORBIDDEN, () -> service.confirm(userId, confirmation("123456")));

        verify(redisUtil, never()).get(anyString());
        verify(userRepository, never()).save(any());
        verify(authService, never()).revokeRefreshTokens(any());
    }

    @Test
    void confirm_wrongOtpChangesNothing() {
        when(authService.confirmsIdentity(user, "secret123", null)).thenReturn(true);
        when(redisUtil.get(otpKey())).thenReturn("123456");
        when(redisUtil.increment("email_change_otp_attempts:" + userId + ":" + NEW_EMAIL)).thenReturn(1L);

        assertFails(GlobalMessage.INVALID_OTP, HttpStatus.BAD_REQUEST, () -> service.confirm(userId, confirmation("000000")));

        assertEquals("old@mail.com", user.getEmail());
        verify(userRepository, never()).save(any());
    }

    @Test
    void confirm_addressTakenSinceTheOtpWasSentIsRefused() {
        when(authService.confirmsIdentity(user, "secret123", null)).thenReturn(true);
        when(redisUtil.get(otpKey())).thenReturn("123456");
        when(redisUtil.increment("email_change_otp_attempts:" + userId + ":" + NEW_EMAIL)).thenReturn(1L);
        when(userRepository.existsByEmailAndIdNot(NEW_EMAIL, userId)).thenReturn(true);

        assertFails(GlobalMessage.EMAIL_ALREADY_TAKEN, HttpStatus.BAD_REQUEST, () -> service.confirm(userId, confirmation("123456")));
        verify(userRepository, never()).save(any());
    }
}
