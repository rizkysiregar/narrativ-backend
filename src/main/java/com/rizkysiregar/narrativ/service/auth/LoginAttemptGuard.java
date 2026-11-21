package com.rizkysiregar.narrativ.service.auth;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Limits wrong passwords per account, next to the per-IP rate limit: guessing one account's password from many IPs
 * (a botnet, rotating proxies) never hits the per-IP limit. After {@code maxFailures} wrong passwords within the
 * window the username is paused until the window ends, from every IP.
 *
 * <p>Counted by the username as typed (trimmed, lower case), whether or not it exists, so the answer never tells
 * which usernames are registered. The pause is short and ends by itself, so locking someone out on purpose only
 * costs them a few minutes; a password reset or Google sign-in still works and a reset ends the pause.
 */
@Component
@Slf4j
public class LoginAttemptGuard {

    static final String KEY_PREFIX = "login_failures:";

    private final RedisUtil redisUtil;
    private final int maxFailures;
    private final long windowSeconds;

    public LoginAttemptGuard(RedisUtil redisUtil,
                             @Value("${app.auth.login-lock.max-failures:5}") int maxFailures,
                             @Value("${app.auth.login-lock.window-minutes:15}") long windowMinutes) {
        this.redisUtil = redisUtil;
        this.maxFailures = maxFailures;
        this.windowSeconds = windowMinutes * 60;
    }

    /** Refuses (429) while the username is paused, before its password is even checked. */
    public void assertNotLocked(String username) {
        String key = key(username);
        Object failures = redisUtil.get(key);
        if (failures != null && Long.parseLong(failures.toString()) >= maxFailures) {
            Long secondsLeft = redisUtil.getExpire(key);
            long minutes = Math.max(1, (secondsLeft == null || secondsLeft < 0 ? windowSeconds : secondsLeft + 59) / 60);
            throw new BusinessException(GlobalMessage.LOGIN_LOCKED, HttpStatus.TOO_MANY_REQUESTS, minutes);
        }
    }

    /** A wrong password: the window starts with the first one and is not extended by the next. */
    public void recordFailure(String username) {
        String key = key(username);
        long failures = redisUtil.increment(key);
        if (failures == 1) {
            redisUtil.expire(key, windowSeconds);
        }
        if (failures == maxFailures) {
            log.warn("AUTH.LOGIN.LOCKED failures={} windowSeconds={}", failures, windowSeconds);
        }
    }

    /** A successful sign-in or a password reset starts over. */
    public void reset(String username) {
        redisUtil.delete(key(username));
    }

    static String key(String username) {
        return KEY_PREFIX + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }
}
