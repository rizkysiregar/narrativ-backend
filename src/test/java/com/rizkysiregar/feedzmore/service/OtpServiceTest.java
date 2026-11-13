package com.rizkysiregar.feedzmore.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.feedzmore.util.RedisUtil;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.model.dto.ResetTokenDTO;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.email.IEmailService;
import com.rizkysiregar.feedzmore.service.otp.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.SentOtpDTO;
import org.springframework.http.HttpStatus;

import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class OtpServiceTest {

    @Mock
    private IEmailService emailService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OtpService otpService;

    @BeforeEach
    void noLockByDefault() {
        lenient().when(redisUtil.hasKey(startsWith("otp_lock:"))).thenReturn(false);
    }


    @Test
    void sendOtp_Success() {
        // Arrange
        String email = "test@mail.com";
        UserEntity userEntity = new UserEntity();
        userEntity.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(userEntity));
        when(redisUtil.hasKey("otp:" + email)).thenReturn(false);
        when(emailService.sendOtpEmail(eq(email), anyString())).thenReturn(true);

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        SentOtpDTO result = otpService.sendOtp(email);

        // Assert
        verify(emailService, times(1)).sendOtpEmail(eq(email), otpCaptor.capture());
        String generatedOtp = otpCaptor.getValue();
        Assertions.assertNotNull(generatedOtp);
        Assertions.assertTrue(generatedOtp.matches("\\d{6}"), "Generated OTP should be 6 digits");

        // The OTP only lives in Redis, with a ttl of 60 seconds: it is never written to the database
        verify(redisUtil, times(1)).set("otp:" + email, generatedOtp, 60);

        // Verify DTO response
        Assertions.assertEquals("t****t@mail.com", result.getOtpSendToEmailAddress());
        Assertions.assertEquals("1", result.getExpirationTimeInMinute());
    }

    @Test
    void sendOtp_UnknownEmail_AnswersLikeARegisteredOneButSendsNothing() {
        String email = "notfound@mail.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        SentOtpDTO result = otpService.sendOtp(email);

        // same answer as for a registered email, so the endpoint does not tell which emails have an account
        Assertions.assertEquals("n****d@mail.com", result.getOtpSendToEmailAddress());
        Assertions.assertEquals("1", result.getExpirationTimeInMinute());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
        // and the OTP is kept like a real one, so everything that follows behaves the same
        verify(redisUtil).set(eq("otp:" + email), argThat(otp -> otp.toString().matches("\\d{6}")), eq(60L));
    }

    @Test
    void sendOtp_DeletedAccount_IsTreatedAsUnknown() {
        String email = "gone@mail.com";
        UserEntity deleted = new UserEntity();
        deleted.setEmail(email);
        deleted.setDeleted(true);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(deleted));

        otpService.sendOtp(email);

        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

    @Test
    void sendOtp_UnknownEmail_SecondRequestIsRateLimitedLikeForARegisteredEmail() {
        String email = "notfound@mail.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(redisUtil.hasKey("otp:" + email)).thenReturn(false, true);

        otpService.sendOtp(email);
        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.sendOtp(email));

        Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
    }


    @Test
    void testGenerateOtp_Return6Digit() {
        try {
            java.lang.reflect.Method method = OtpService.class.getDeclaredMethod("generateOtp");
            method.setAccessible(true);
            Object result = method.invoke(otpService);
            Assertions.assertNotNull(result, "OTP should not be null");
            String otp = result.toString();
            Assertions.assertEquals(6, otp.length(), "OTP should be 6 digits long");
            Assertions.assertTrue(otp.matches("\\d{6}"), "OTP should contain only digits");
        } catch (Exception e) {
            Assertions.fail("Failed to invoke generateOtp via reflection: " + e.getMessage());
        }
    }

    @Test
    void sendOtp_TooManyRequests_WhenRedisHasKey() {
        String email = "test@mail.com";
        when(redisUtil.hasKey("otp:" + email)).thenReturn(true);

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.sendOtp(email));
        Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
        verify(redisUtil, never()).set(anyString(), any(), anyInt());
        // decided before the user is looked up, so it is the same for every email
        verifyNoInteractions(userRepository);
    }

    @Test
    void sendOtp_EmailSendFailure_ThrowsInternalError() {
        String email = "test@mail.com";
        UserEntity userEntity = new UserEntity();
        userEntity.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(userEntity));
        when(redisUtil.hasKey("otp:" + email)).thenReturn(false);
        when(emailService.sendOtpEmail(eq(email), anyString())).thenReturn(false);

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.sendOtp(email));
        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatus());
        verify(redisUtil, never()).set(anyString(), any(), anyInt());
    }

    @Test
    void verifyOtp_Success() {
        // Arrange
        String email = "test@mail.com";
        String otp = "123456";
        when(redisUtil.get("otp:" + email)).thenReturn(otp);

        // Act
        ResetTokenDTO resetTokenDTO = otpService.verifyOtp(email, otp);

        // Assert
        Assertions.assertNotNull(resetTokenDTO);
        Assertions.assertNotNull(resetTokenDTO.getResetToken());
        // verify redis get and set interactions
        verify(redisUtil, times(1)).get("otp:" + email);
        verify(redisUtil, times(1)).set("reset_token:" + email, resetTokenDTO.getResetToken(), 600);
        // reset token should look like a UUID
        Assertions.assertTrue(resetTokenDTO.getResetToken().matches("[a-f0-9\\-]{36}"), "Reset token should be a UUID string");
    }


    @Test
    void verifyOtp_Otp_Not_Found_Or_Expired() {
        // Arrange
        String email = "test@mail.com";
        String otp = "123456";
        when(redisUtil.get("otp:" + email)).thenReturn(null);

        // Act & Assert
        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.verifyOtp(email, otp));
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        Assertions.assertEquals(GlobalMessage.OTP_EXPIRED.getMessage(), ex.getMessage());
        verify(redisUtil, times(1)).get("otp:" + email);
    }

    @Test
    void verifyOtp_Otp_Not_Match() {
        // Arrange
        String email = "test@mail.com";
        String otp = "123456";
        when(redisUtil.get("otp:" + email)).thenReturn("12345");

        // Act & Assert
        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.verifyOtp(email, otp));
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        Assertions.assertEquals(GlobalMessage.INVALID_OTP.getMessage(), ex.getMessage());
        verify(redisUtil, times(1)).get("otp:" + email);
    }


    @Test
    void verifyOtp_Null_Input_Should_Return_InvalidOtp() {
        // Arrange
        String email = "test@mail.com";
        when(redisUtil.get("otp:" + email)).thenReturn("654321");

        // Act & Assert
        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.verifyOtp(email, null));
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        Assertions.assertEquals(GlobalMessage.INVALID_OTP.getMessage(), ex.getMessage());
        verify(redisUtil, times(1)).get("otp:" + email);
    }

    @Test
    void verifyOtp_Empty_Input_Should_Return_InvalidOtp() {
        // Arrange
        String email = "test@mail.com";
        when(redisUtil.get("otp:" + email)).thenReturn("654321");

        // Act & Assert
        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.verifyOtp(email, ""));
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        Assertions.assertEquals(GlobalMessage.INVALID_OTP.getMessage(), ex.getMessage());
        verify(redisUtil, times(1)).get("otp:" + email);
    }

    @Test
    void verifyOtp_Success_ClearsAttemptCounter() {
        String email = "test@mail.com";
        when(redisUtil.get("otp:" + email)).thenReturn("123456");
        when(redisUtil.increment("otp_attempts:" + email)).thenReturn(3L);

        otpService.verifyOtp(email, "123456");

        verify(redisUtil).delete("otp_attempts:" + email);
    }

    @Test
    void verifyOtp_Success_DeletesOtpSoItCannotBeReused() {
        String email = "test@mail.com";
        when(redisUtil.get("otp:" + email)).thenReturn("123456");
        when(redisUtil.increment("otp_attempts:" + email)).thenReturn(1L);

        otpService.verifyOtp(email, "123456");

        verify(redisUtil).delete("otp:" + email);
    }

    @Test
    void verifyOtp_WrongOtp_DoesNotDeleteOtpBeforeLimit() {
        String email = "test@mail.com";
        when(redisUtil.get("otp:" + email)).thenReturn("123456");
        when(redisUtil.increment("otp_attempts:" + email)).thenReturn(2L);

        Assertions.assertThrows(BusinessException.class, () -> otpService.verifyOtp(email, "000000"));

        verify(redisUtil, never()).delete("otp:" + email);
    }

    @Test
    void verifyOtp_WrongOtp_FirstAttempt_StartsCounterWithTtl() {
        String email = "test@mail.com";
        when(redisUtil.get("otp:" + email)).thenReturn("123456");
        when(redisUtil.increment("otp_attempts:" + email)).thenReturn(1L);

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.verifyOtp(email, "000000"));

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        verify(redisUtil).expire("otp_attempts:" + email, 60);
        verify(redisUtil, never()).delete("otp:" + email);
    }

    @Test
    void verifyOtp_WrongOtp_FourthAttempt_StillInvalidOtp() {
        String email = "test@mail.com";
        when(redisUtil.get("otp:" + email)).thenReturn("123456");
        when(redisUtil.increment("otp_attempts:" + email)).thenReturn(4L);

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.verifyOtp(email, "000000"));

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        Assertions.assertEquals(GlobalMessage.INVALID_OTP.getMessage(), ex.getMessage());
        verify(redisUtil, never()).set(eq("otp_lock:" + email), any(), anyLong());
    }

    @Test
    void verifyOtp_WrongOtp_FifthAttempt_BurnsOtpAndLocksEmail() {
        String email = "test@mail.com";
        when(redisUtil.get("otp:" + email)).thenReturn("123456");
        when(redisUtil.increment("otp_attempts:" + email)).thenReturn(5L);

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.verifyOtp(email, "000000"));

        Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
        Assertions.assertEquals(GlobalMessage.TOO_MANY_REQUEST_OTP.getMessage(), ex.getMessage());
        verify(redisUtil).delete("otp:" + email);
        verify(redisUtil).delete("otp_attempts:" + email);
        verify(redisUtil).set("otp_lock:" + email, "1", 300L);
    }

    @Test
    void verifyOtp_SixthAttempt_RejectedEvenWithCorrectOtp() {
        // e.g. parallel requests that all passed the lock check before it was written
        String email = "test@mail.com";
        when(redisUtil.get("otp:" + email)).thenReturn("123456");
        when(redisUtil.increment("otp_attempts:" + email)).thenReturn(6L);

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.verifyOtp(email, "123456"));

        Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
        verify(redisUtil, never()).set(eq("reset_token:" + email), any(), anyLong());
    }

    @Test
    void verifyOtp_WhenLocked_RejectedWithoutTouchingOtp() {
        String email = "test@mail.com";
        when(redisUtil.hasKey("otp_lock:" + email)).thenReturn(true);

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.verifyOtp(email, "123456"));

        Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
        verify(redisUtil, never()).get(anyString());
        verify(redisUtil, never()).increment(anyString());
    }

    @Test
    void sendOtp_WhenLocked_RejectedBeforeSendingEmail() {
        String email = "test@mail.com";
        when(redisUtil.hasKey("otp_lock:" + email)).thenReturn(true);

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.sendOtp(email));

        Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
    }

}
