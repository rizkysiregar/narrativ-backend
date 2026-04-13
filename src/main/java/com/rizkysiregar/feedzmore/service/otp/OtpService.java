package com.rizkysiregar.feedzmore.service.otp;

import com.rizkysiregar.feedzmore.util.RedisUtil;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class OtpService implements IOtpService {

    private final OtpCodeRepository otpCodeRepository;
    private final IEmailService emailService;
    private final UserRepository userRepository;
    private final RedisUtil redisUtil;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${otp.retry-time-in-seconds}")
    String otpRetryTimeInSeconds;

    public OtpService(OtpCodeRepository otpCodeRepository, IEmailService emailService, UserRepository userRepository, RedisUtil redisUtil) {
        this.otpCodeRepository = otpCodeRepository;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.redisUtil = redisUtil;
    }

    @Override
    public SentOtpDTO sendOtp(String email) {
        Optional<UserEntity> user = userRepository.findByEmail(email);

        if (user.isEmpty()) {
            throw new BusinessException(GlobalMessage.NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        if (redisUtil.hasKey("otp:" + email)) {
            throw new BusinessException(GlobalMessage.TOO_MANY_REQUEST_OTP, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }

        String otp = generateOtp();
        LocalDateTime expirationTime = LocalDateTime.now().plusSeconds(Long.parseLong(otpRetryTimeInSeconds));

        // sending otp via email
        boolean sendStatus = emailService.sendOtpEmail(email, otp);
        if (!sendStatus) {
            log.error("## Failed to send OTP to email: {}", email);
            throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        OtpCode otpCode = OtpCode.builder()
                .email(email)
                .otp(otp)
                .expirationTime(expirationTime)
                .build();
        otpCodeRepository.save(otpCode);
        redisUtil.set("otp:" + email, otp, 60);

        return SentOtpDTO.builder()
                .otpSendToEmailAddress(EmailMasking.maskEmail(email))
                .expirationTimeInMinute("1")
                .build();

    }

    private String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    @Override
    public ResetTokenDTO verifyOtp(String email, String otp) {
        Object actualOtp = redisUtil.get("otp:" + email);

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
