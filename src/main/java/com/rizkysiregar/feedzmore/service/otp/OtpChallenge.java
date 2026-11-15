package com.rizkysiregar.feedzmore.service.otp;

import com.rizkysiregar.feedzmore.constant.GlobalMessage;
import com.rizkysiregar.feedzmore.exception.BusinessException;
import com.rizkysiregar.feedzmore.util.EmailMasking;
import com.rizkysiregar.feedzmore.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.security.SecureRandom;

/**
 * The Redis side of one kind of OTP (reset password, registration): the code itself, the guess counter and the lock.
 * Every kind has its own key prefix, so a code issued for one purpose never works for another.
 */
@Slf4j
class OtpChallenge {

    // a 6 digit OTP has only 1M combinations, so the number of guesses per OTP must stay tiny
    static final long MAX_VERIFY_ATTEMPTS = 5;
    static final long LOCK_SECONDS = 5 * 60;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RedisUtil redisUtil;
    private final String otpKeyPrefix;
    private final String attemptsKeyPrefix;
    private final String lockKeyPrefix;
    private final long ttlSeconds;

    OtpChallenge(RedisUtil redisUtil, String name, long ttlSeconds) {
        this.redisUtil = redisUtil;
        this.otpKeyPrefix = name + ":";
        this.attemptsKeyPrefix = name + "_attempts:";
        this.lockKeyPrefix = name + "_lock:";
        this.ttlSeconds = ttlSeconds;
    }

    static String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    void assertNotLocked(String email) {
        if (redisUtil.hasKey(lockKeyPrefix + email)) {
            throw new BusinessException(GlobalMessage.TOO_MANY_REQUEST_OTP, HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    boolean hasPendingOtp(String email) {
        return redisUtil.hasKey(otpKeyPrefix + email);
    }

    void store(String email, String otp) {
        redisUtil.set(otpKeyPrefix + email, otp, ttlSeconds);
        redisUtil.delete(attemptsKeyPrefix + email); // a fresh OTP starts with a fresh attempt budget
    }

    /** Returns normally only for the right OTP, which is burned right away so it cannot be replayed. */
    void verify(String email, String otp) {
        assertNotLocked(email);

        Object actualOtp = redisUtil.get(otpKeyPrefix + email);

        if (actualOtp == null) {
            throw new BusinessException(GlobalMessage.OTP_EXPIRED, HttpStatus.BAD_REQUEST);
        }

        // every guess is counted before it is compared, INCR is atomic so parallel requests cannot dodge the limit
        String attemptsKey = attemptsKeyPrefix + email;
        long attempts = redisUtil.increment(attemptsKey);
        if (attempts == 1) {
            redisUtil.expire(attemptsKey, ttlSeconds);
        }
        if (attempts > MAX_VERIFY_ATTEMPTS) {
            throw new BusinessException(GlobalMessage.TOO_MANY_REQUEST_OTP, HttpStatus.TOO_MANY_REQUESTS);
        }

        if (actualOtp.equals(otp)) {
            redisUtil.delete(otpKeyPrefix + email);
            redisUtil.delete(attemptsKey);
            return;
        }

        log.info("## OTP not matched, attempt {}/{}", attempts, MAX_VERIFY_ATTEMPTS);
        if (attempts == MAX_VERIFY_ATTEMPTS) {
            // budget spent: burn the OTP and block both verify and re-send for a while
            log.warn("## OTP verify limit reached for email: {}", EmailMasking.maskEmail(email));
            redisUtil.delete(otpKeyPrefix + email);
            redisUtil.delete(attemptsKey);
            redisUtil.set(lockKeyPrefix + email, "1", LOCK_SECONDS);
            throw new BusinessException(GlobalMessage.TOO_MANY_REQUEST_OTP, HttpStatus.TOO_MANY_REQUESTS);
        }
        throw new BusinessException(GlobalMessage.INVALID_OTP, HttpStatus.BAD_REQUEST);
    }
}
