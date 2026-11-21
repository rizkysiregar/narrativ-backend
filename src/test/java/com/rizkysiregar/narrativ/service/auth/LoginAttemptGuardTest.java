package com.rizkysiregar.narrativ.service.auth;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginAttemptGuardTest {

    private final RedisUtil redisUtil = mock(RedisUtil.class);
    // a tiny in-memory Redis: counters and their TTL
    private final Map<String, Long> counters = new HashMap<>();
    private final Map<String, Long> ttls = new HashMap<>();
    private final LoginAttemptGuard guard = new LoginAttemptGuard(redisUtil, 3, 15);

    @BeforeEach
    void fakeRedis() {
        when(redisUtil.increment(anyString())).thenAnswer(call -> counters.merge(call.getArgument(0), 1L, Long::sum));
        when(redisUtil.get(anyString())).thenAnswer(call -> counters.get(call.<String>getArgument(0)));
        when(redisUtil.getExpire(anyString())).thenAnswer(call -> ttls.getOrDefault(call.<String>getArgument(0), -1L));
        doAnswer(call -> ttls.put(call.getArgument(0), call.getArgument(1))).when(redisUtil).expire(anyString(), anyLong());
        doAnswer(call -> {
            counters.remove(call.<String>getArgument(0));
            ttls.remove(call.<String>getArgument(0));
            return null;
        }).when(redisUtil).delete(anyString());
    }

    @Test
    void pausesTheAccountAfterTooManyWrongPasswords() {
        guard.recordFailure("ani");
        guard.recordFailure("ani");
        assertThatCode(() -> guard.assertNotLocked("ani")).doesNotThrowAnyException();

        guard.recordFailure("ani");

        assertThatThrownBy(() -> guard.assertNotLocked("ani"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getGlobalMessage()).isEqualTo(GlobalMessage.LOGIN_LOCKED);
                    assertThat(e.getArgs()).containsExactly(15L);
                });
    }

    @Test
    void theWindowStartsWithTheFirstFailureAndIsNotExtended() {
        guard.recordFailure("ani");
        ttls.put(LoginAttemptGuard.key("ani"), 120L); // two minutes later in the window
        guard.recordFailure("ani");

        assertThat(ttls.get(LoginAttemptGuard.key("ani"))).isEqualTo(120L);
    }

    @Test
    void tellsTheMinutesLeftRoundedUp() {
        for (int i = 0; i < 3; i++) {
            guard.recordFailure("ani");
        }
        ttls.put(LoginAttemptGuard.key("ani"), 61L);

        assertThatThrownBy(() -> guard.assertNotLocked("ani"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getArgs()).containsExactly(2L));
    }

    @Test
    void countsTheUsernameHoweverItIsTyped() {
        guard.recordFailure("Ani");
        guard.recordFailure(" ani ");
        guard.recordFailure("ANI");

        assertThatThrownBy(() -> guard.assertNotLocked("ani")).isInstanceOf(BusinessException.class);
        // other accounts are untouched
        assertThatCode(() -> guard.assertNotLocked("budi")).doesNotThrowAnyException();
    }

    @Test
    void resetStartsOver() {
        for (int i = 0; i < 3; i++) {
            guard.recordFailure("ani");
        }

        guard.reset("ani");

        assertThatCode(() -> guard.assertNotLocked("ani")).doesNotThrowAnyException();
    }
}
