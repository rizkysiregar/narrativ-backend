package com.rizkysiregar.feedzmore.service.otp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rizkysiregar.feedzmore.config.RedisUtil;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.OtpCode;
import com.rizkysiregar.feedzmore.model.dto.ResetTokenDTO;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OtpService implements IOtpService {

    @Autowired
    private OtpCodeRepository otpCodeRepository;

    @Autowired
    private IEmailService emailService;


    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${otp.retry-time-in-seconds}")
    String otpRetryTimeInSeconds;

    @Override
    public SentOtpDTO sendOtp(String email) {
        log.info("## rcv: send otp to: {}", email);
        log.info("## retry in seconds: {}", otpRetryTimeInSeconds);
        Optional<UserEntity> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            if (Boolean.TRUE.equals(redisUtil.hasKey("otp:" + email))) {
                throw new BusinessException(GlobalMessage.TOO_MANY_REQUEST_OTP, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }

            String otp = generateOtp();
            LocalDateTime expirationTime = LocalDateTime.now().plusSeconds(Long.parseLong(otpRetryTimeInSeconds));

//            String message = "Your password reset OTP is: " + otp;
//            emailService.sendEmail(email, "Password Reset OTP", message);
            emailService.sendOtpEmail(email, otp);

            OtpCode otpCode = OtpCode.builder()
                    .email(email)
                    .otp(otp)
                    .expirationTime(expirationTime)
                    .build();
            otpCodeRepository.save(otpCode);
            redisUtil.set("otp:" + email, otp, 60);

            return SentOtpDTO.builder()
                    .otpSendToEmailAddress(EmailMasking.maskEmail(email))
                    // .expirationTimeInMinute(String.valueOf(Long.parseLong(otpRetryTimeInSeconds) / 60))
                    .expirationTimeInMinute("1")
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
    public ResetTokenDTO verifyOtp(String email, String otp) {
        Object actualOtp = redisUtil.get("otp:" + email);
        log.info("## Provided otp: {}", otp);
        log.info("## Actual otp: {}", actualOtp);

        if (actualOtp == null) {
            throw new BusinessException(GlobalMessage.OTP_EXPIRED, HttpStatus.BAD_REQUEST);
        }

        if (actualOtp.equals(otp)) {
            String resetToken = UUID.randomUUID().toString();
            String resetTokenKey = "reset_token:".concat(email);
            redisUtil.set(resetTokenKey, resetToken, 600);

            return ResetTokenDTO.builder()
                    .resetToken(resetToken)
                    .build();
        } else {
            log.info("## OTP not matched");
            throw new BusinessException(GlobalMessage.INVALID_OTP, HttpStatus.BAD_REQUEST);
        }
    }


}
