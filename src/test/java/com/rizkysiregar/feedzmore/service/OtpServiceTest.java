package com.rizkysiregar.feedzmore.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.feedzmore.config.RedisUtil;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.repository.OtpCodeRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.email.IEmailService;
import com.rizkysiregar.feedzmore.service.otp.OtpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.OtpCode;
import com.rizkysiregar.feedzmore.model.dto.SentOtpDTO;
import org.springframework.http.HttpStatus;

import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class OtpServiceTest {

    @Mock
    private OtpCodeRepository otpCodeRepository;
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


    @Test
    void sendOtp_Success() {
        // Arrange
        String email = "test@mail.com";
        UserEntity userEntity = new UserEntity();
        userEntity.setEmail(email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(userEntity));
        when(redisUtil.hasKey("otp:" + email)).thenReturn(false);
        when(emailService.sendOtpEmail(eq(email), anyString())).thenReturn(true);

        // set @Value field
        ReflectionTestUtils.setField(otpService, "otpRetryTimeInSeconds", "60");

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        SentOtpDTO result = otpService.sendOtp(email);

        // Assert
        verify(emailService, times(1)).sendOtpEmail(eq(email), otpCaptor.capture());
        String generatedOtp = otpCaptor.getValue();
        Assertions.assertNotNull(generatedOtp);
        Assertions.assertTrue(generatedOtp.matches("\\d{6}"), "Generated OTP should be 6 digits");

        // Verify repository save with same OTP and email
        ArgumentCaptor<OtpCode> otpCodeCaptor = ArgumentCaptor.forClass(OtpCode.class);
        verify(otpCodeRepository, times(1)).save(otpCodeCaptor.capture());
        OtpCode saved = otpCodeCaptor.getValue();
        Assertions.assertEquals(email, saved.getEmail());
        Assertions.assertEquals(generatedOtp, saved.getOtp());
        Assertions.assertNotNull(saved.getExpirationTime());

        // Verify Redis set with ttl 60
        verify(redisUtil, times(1)).set("otp:" + email, generatedOtp, 60);

        // Verify DTO response
        Assertions.assertEquals("t****t@mail.com", result.getOtpSendToEmailAddress());
        Assertions.assertEquals("1", result.getExpirationTimeInMinute());
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
    void sendOtp_UserNotFound_ThrowsNotFound() {
        String email = "notfound@mail.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.sendOtp(email));
        Assertions.assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    void sendOtp_TooManyRequests_WhenRedisHasKey() {
        String email = "test@mail.com";
        UserEntity userEntity = new UserEntity();
        userEntity.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(userEntity));
        when(redisUtil.hasKey("otp:" + email)).thenReturn(true);

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.sendOtp(email));
        Assertions.assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, ex.getHttpStatus());
        verify(emailService, never()).sendOtpEmail(anyString(), anyString());
        verify(otpCodeRepository, never()).save(any());
        verify(redisUtil, never()).set(anyString(), any(), anyInt());
    }

    @Test
    void sendOtp_EmailSendFailure_ThrowsInternalError() {
        String email = "test@mail.com";
        UserEntity userEntity = new UserEntity();
        userEntity.setEmail(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(userEntity));
        when(redisUtil.hasKey("otp:" + email)).thenReturn(false);
        when(emailService.sendOtpEmail(eq(email), anyString())).thenReturn(false);
        ReflectionTestUtils.setField(otpService, "otpRetryTimeInSeconds", "60");

        BusinessException ex = Assertions.assertThrows(BusinessException.class, () -> otpService.sendOtp(email));
        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatus());
        verify(otpCodeRepository, never()).save(any());
        verify(redisUtil, never()).set(anyString(), any(), anyInt());
    }

}
