package com.rizkysiregar.feedzmore.service.otp;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.OtpCode;
import com.rizkysiregar.feedzmore.model.dto.SentOtpDTO;
import com.rizkysiregar.feedzmore.model.entity.UserEntity;
import com.rizkysiregar.feedzmore.repository.OtpCodeRepository;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.email.IEmailService;
import com.rizkysiregar.feedzmore.util.EmailMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OtpService implements IOtpService {

    @Autowired
    private OtpCodeRepository otpCodeRepository;

    @Autowired
    private IEmailService emailService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Value("${otp.retry-time}")
    String otpRetryTime;

    @Override
    public SentOtpDTO sendOtp(String email) {
        Optional<UserEntity> byEmail = userRepository.findByEmail(email);

        if (byEmail.isPresent()) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey("otp:" + email))) {
                throw new BusinessException(GlobalMessage.TOO_MANY_REQUEST_OTP, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }
            String otp = generateOtp();
            LocalDateTime expirationTime = LocalDateTime.now().plusMinutes(Long.parseLong(otpRetryTime)); // set expires to 5 min

            OtpCode otpCode = new OtpCode(email, otp, expirationTime);
            otpCodeRepository.save(otpCode);
            saveOtpToRedis(otp, email);

            String message = "Your password reset OTP is: " + otp;
            emailService.sendEmail(email, "Password Reset OTP", message);
            return SentOtpDTO.builder()
                    .otpSendToEmailAddress(EmailMasking.maskEmail(email))
                    .expirationTimeInMinute(otpRetryTime)
                    .build();
        } else {
            throw new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND);
        }
    }

    private String generateOtp() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000)); // Generate 6 digit OTP code
    }

    @Override
    public boolean verifyOtp(String email, String otp) {
        String actualOtp = getOtp(email);
        log.info("## Actual otp: {}", actualOtp);

        if (actualOtp == null) {
            throw new BusinessException(GlobalMessage.INVALID_OTP, HttpStatus.BAD_REQUEST);
        }

        return actualOtp.equals(otp);
    }

    private void saveOtpToRedis(String otp, String email) {
        String redisKey = "otp:" + email;
        redisTemplate.opsForValue().set(redisKey, otp, Long.parseLong(otpRetryTime), TimeUnit.MINUTES);
    }

    private String getOtp(String email) {
        return redisTemplate.opsForValue().get("otp:" + email);
    }

    private void deleteOtp(String email) {
        redisTemplate.delete("otp:" + email);
    }
}
