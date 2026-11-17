package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.dto.RegisterOtpDTO;
import com.rizkysiregar.narrativ.model.dto.RegistrationTokenDTO;
import com.rizkysiregar.narrativ.model.entity.UserEntity;
import com.rizkysiregar.narrativ.repository.UserRepository;
import com.rizkysiregar.narrativ.service.email.IEmailService;
import com.rizkysiregar.narrativ.service.otp.RegistrationOtpService;
import com.rizkysiregar.narrativ.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationOtpServiceTest {

    private static final String EMAIL = "new@mail.com";

    @Mock private IEmailService emailService;
    @Mock private UserRepository userRepository;
    @Mock private RedisUtil redisUtil;

    private RegistrationOtpService service;

    @BeforeEach
    void enabledService() {
        service = new RegistrationOtpService(emailService, userRepository, redisUtil, new MockEnvironment(), true);
    }

    @Test
    void sendOtp_SendsAnOtpValidForFiveMinutes() {
        when(redisUtil.setIfAbsent("register_otp_cooldown:" + EMAIL, "1", 60)).thenReturn(true);
        when(emailService.sendOtpEmail(eq(EMAIL), anyString())).thenReturn(true);
        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);

        RegisterOtpDTO result = service.sendOtp(EMAIL);

        verify(emailService).sendOtpEmail(eq(EMAIL), otp.capture());
        assertTrue(otp.getValue().matches("\\d{6}"));
        verify(redisUtil).set("register_otp:" + EMAIL, otp.getValue(), 300L);
        assertTrue(result.isVerificationRequired());
        assertEquals("n****w@mail.com", result.getOtpSendToEmailAddress());
        assertEquals("5", result.getExpirationTimeInMinute());
    }

    @Test
    void sendOtp_DoesNotTouchTheResetPasswordKeys() {
        when(redisUtil.setIfAbsent(anyString(), any(), anyLong())).thenReturn(true);
        when(emailService.sendOtpEmail(eq(EMAIL), anyString())).thenReturn(true);

        service.sendOtp(EMAIL);

        verify(redisUtil, never()).set(eq("otp:" + EMAIL), any(), anyLong());
    }

    @Test
    void sendOtp_EmailAlreadyRegistered_Rejected() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(new UserEntity()));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.sendOtp(EMAIL));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals(GlobalMessage.EMAIL_ALREADY_TAKEN.getMessage(), ex.getMessage());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void sendOtp_WithinCooldown_Rejected() {
        when(redisUtil.setIfAbsent("register_otp_cooldown:" + EMAIL, "1", 60)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.sendOtp(EMAIL));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
        assertEquals(GlobalMessage.OTP_RESEND_COOLDOWN.getMessage(), ex.getMessage());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void sendOtp_WhenLocked_Rejected() {
        when(redisUtil.hasKey("register_otp_lock:" + EMAIL)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.sendOtp(EMAIL));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void sendOtp_EmailFailure_FreesTheCooldownAndStoresNothing() {
        when(redisUtil.setIfAbsent(anyString(), any(), anyLong())).thenReturn(true);
        when(emailService.sendOtpEmail(eq(EMAIL), anyString())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.sendOtp(EMAIL));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatus());
        verify(redisUtil).delete("register_otp_cooldown:" + EMAIL);
        verify(redisUtil, never()).set(anyString(), any(), anyLong());
    }

    @Test
    void verifyOtp_RightOtp_IssuesATokenForTenMinutes() {
        when(redisUtil.get("register_otp:" + EMAIL)).thenReturn("123456");
        when(redisUtil.increment("register_otp_attempts:" + EMAIL)).thenReturn(1L);

        RegistrationTokenDTO result = service.verifyOtp(EMAIL, "123456");

        assertTrue(result.getRegistrationToken().matches("[a-f0-9\\-]{36}"));
        verify(redisUtil).set("register_token:" + EMAIL, result.getRegistrationToken(), 600L);
        verify(redisUtil).delete("register_otp:" + EMAIL);
    }

    @Test
    void verifyOtp_WrongOtp_IssuesNoToken() {
        when(redisUtil.get("register_otp:" + EMAIL)).thenReturn("123456");
        when(redisUtil.increment("register_otp_attempts:" + EMAIL)).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.verifyOtp(EMAIL, "000000"));

        assertEquals(GlobalMessage.INVALID_OTP.getMessage(), ex.getMessage());
        verify(redisUtil, never()).set(eq("register_token:" + EMAIL), any(), anyLong());
    }

    @Test
    void verifyOtp_FifthWrongGuess_LocksTheEmail() {
        when(redisUtil.get("register_otp:" + EMAIL)).thenReturn("123456");
        when(redisUtil.increment("register_otp_attempts:" + EMAIL)).thenReturn(5L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.verifyOtp(EMAIL, "000000"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
        verify(redisUtil).delete("register_otp:" + EMAIL);
        verify(redisUtil).set("register_otp_lock:" + EMAIL, "1", 300L);
    }

    @Test
    void verifyOtp_Expired() {
        when(redisUtil.get("register_otp:" + EMAIL)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.verifyOtp(EMAIL, "123456"));

        assertEquals(GlobalMessage.OTP_EXPIRED.getMessage(), ex.getMessage());
    }

    @Test
    void consumeRegistrationToken_RightToken_IsBurned() {
        when(redisUtil.get("register_token:" + EMAIL)).thenReturn("token-1");

        service.consumeRegistrationToken(EMAIL, "token-1");

        verify(redisUtil).delete("register_token:" + EMAIL);
    }

    @Test
    void consumeRegistrationToken_WrongMissingOrAbsent_Rejected() {
        when(redisUtil.get("register_token:" + EMAIL)).thenReturn("token-1", "token-1", null);

        for (String given : new String[]{"other", null, "token-1"}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.consumeRegistrationToken(EMAIL, given));
            assertEquals(GlobalMessage.EMAIL_NOT_VERIFIED.getMessage(), ex.getMessage());
        }
        verify(redisUtil, never()).delete(anyString());
    }

    @Test
    void disabled_SendOtpSendsNothingAndSaysSo() {
        RegistrationOtpService off = new RegistrationOtpService(emailService, userRepository, redisUtil,
                new MockEnvironment(), false);

        RegisterOtpDTO result = off.sendOtp(EMAIL);

        assertFalse(result.isVerificationRequired());
        assertNull(result.getOtpSendToEmailAddress());
        assertFalse(off.isVerificationRequired());
        verifyNoInteractions(emailService, redisUtil, userRepository);
    }

    @Test
    void disabled_VerifyOtpIsRejected() {
        RegistrationOtpService off = new RegistrationOtpService(emailService, userRepository, redisUtil,
                new MockEnvironment(), false);

        assertThrows(BusinessException.class, () -> off.verifyOtp(EMAIL, "123456"));
    }

    @Test
    void prodProfile_RefusesToStartWithVerificationOff() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class,
                () -> new RegistrationOtpService(emailService, userRepository, redisUtil, prod, false));
        assertDoesNotThrow(() -> new RegistrationOtpService(emailService, userRepository, redisUtil, prod, true));
    }
}
