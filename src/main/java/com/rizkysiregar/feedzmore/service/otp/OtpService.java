package com.rizkysiregar.feedzmore.service.otp;

import com.rizkysiregar.feedzmore.util.RedisUtil;
import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.model.dto.ResetTokenDTO;
import com.rizkysiregar.feedzmore.model.dto.SentOtpDTO;
import com.rizkysiregar.feedzmore.repository.UserRepository;
import com.rizkysiregar.feedzmore.service.email.IEmailService;
import com.rizkysiregar.feedzmore.util.EmailMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;

@Slf4j
@Service
public class OtpService implements IOtpService {

    private final IEmailService emailService;
    private final UserRepository userRepository;
    private final RedisUtil redisUtil;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String OTP_ATTEMPTS_KEY_PREFIX = "otp_attempts:";
    private static final String OTP_LOCK_KEY_PREFIX = "otp_lock:";
    private static final long OTP_TTL_SECONDS = 60;
    // a 6 digit OTP has only 1M combinations, so the number of guesses per OTP must stay tiny
    private static final long MAX_VERIFY_ATTEMPTS = 5;
    private static final long LOCK_SECONDS = 5 * 60;

    public OtpService(IEmailService emailService, UserRepository userRepository, RedisUtil redisUtil) {
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.redisUtil = redisUtil;
    }

    @Override
    public SentOtpDTO sendOtp(String email) {
        if (redisUtil.hasKey(OTP_LOCK_KEY_PREFIX + email)) {
            throw new BusinessException(GlobalMessage.TOO_MANY_REQUEST_OTP, HttpStatus.TOO_MANY_REQUESTS);
        }

        if (redisUtil.hasKey(OTP_KEY_PREFIX + email)) {
            throw new BusinessException(GlobalMessage.TOO_MANY_REQUEST_OTP, HttpStatus.TOO_MANY_REQUESTS);
        }

        String otp = generateOtp();

        // The answer is the same whether or not the email is registered, otherwise this endpoint tells anybody which
        // emails have an account. An unknown email gets an OTP that nobody receives: it is kept like a real one, so
        // asking again or guessing it behaves exactly the same (too many requests, wrong code, lock after 5 guesses).
        boolean registered = userRepository.findByEmail(email).filter(user -> !user.isDeleted()).isPresent();
        if (registered) {
            boolean sendStatus = emailService.sendOtpEmail(email, otp);
            if (!sendStatus) {
                log.error("## Failed to send OTP to email: {}", EmailMasking.maskEmail(email));
                throw new BusinessException(GlobalMessage.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            log.info("## OTP requested for an email without an account: {}", EmailMasking.maskEmail(email));
        }

        redisUtil.set(OTP_KEY_PREFIX + email, otp, OTP_TTL_SECONDS);
        redisUtil.delete(OTP_ATTEMPTS_KEY_PREFIX + email); // a fresh OTP starts with a fresh attempt budget

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
        if (redisUtil.hasKey(OTP_LOCK_KEY_PREFIX + email)) {
            throw new BusinessException(GlobalMessage.TOO_MANY_REQUEST_OTP, HttpStatus.TOO_MANY_REQUESTS);
        }

        Object actualOtp = redisUtil.get(OTP_KEY_PREFIX + email);

        if (actualOtp == null) {
            throw new BusinessException(GlobalMessage.OTP_EXPIRED, HttpStatus.BAD_REQUEST);
        }

        // every guess is counted before it is compared, INCR is atomic so parallel requests cannot dodge the limit
        String attemptsKey = OTP_ATTEMPTS_KEY_PREFIX + email;
        long attempts = redisUtil.increment(attemptsKey);
        if (attempts == 1) {
            redisUtil.expire(attemptsKey, OTP_TTL_SECONDS);
        }
        if (attempts > MAX_VERIFY_ATTEMPTS) {
            throw new BusinessException(GlobalMessage.TOO_MANY_REQUEST_OTP, HttpStatus.TOO_MANY_REQUESTS);
        }

        if (actualOtp.equals(otp)) {
            // one time use: burn the OTP right away so it cannot be replayed
            redisUtil.delete(OTP_KEY_PREFIX + email);
            redisUtil.delete(attemptsKey);
            String resetToken = UUID.randomUUID().toString();
            String resetTokenKey = "reset_token:".concat(email);
            redisUtil.set(resetTokenKey, resetToken, 600);

            return ResetTokenDTO.builder()
                    .resetToken(resetToken)
                    .build();
        }

        log.info("## OTP not matched, attempt {}/{}", attempts, MAX_VERIFY_ATTEMPTS);
        if (attempts == MAX_VERIFY_ATTEMPTS) {
            // budget spent: burn the OTP and block both verify and re-send for a while
            log.warn("## OTP verify limit reached for email: {}", EmailMasking.maskEmail(email));
            redisUtil.delete(OTP_KEY_PREFIX + email);
            redisUtil.delete(attemptsKey);
            redisUtil.set(OTP_LOCK_KEY_PREFIX + email, "1", LOCK_SECONDS);
            throw new BusinessException(GlobalMessage.TOO_MANY_REQUEST_OTP, HttpStatus.TOO_MANY_REQUESTS);
        }
        throw new BusinessException(GlobalMessage.INVALID_OTP, HttpStatus.BAD_REQUEST);
    }

}
