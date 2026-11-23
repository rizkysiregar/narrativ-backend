package com.rizkysiregar.narrativ.service;

import com.rizkysiregar.narrativ.constant.GlobalMessage;
import com.rizkysiregar.narrativ.exception.BusinessException;
import com.rizkysiregar.narrativ.model.entity.ParameterEntity;
import com.rizkysiregar.narrativ.repository.ParameterRepository;
import com.rizkysiregar.narrativ.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * On/off switches for features the admin can show or hide without a release, kept as app parameters under
 * {@code feature.*} with the value {@code true} or {@code false} (seeded by V19). A missing or unreadable switch is
 * off, so a feature never shows by accident.
 * <p>
 * The switches are read on every app start and on the chat endpoints, so they are cached in Redis as one map. The
 * admin parameter service drops the cache after a change commits; the TTL is only a safety net. When Redis is down the
 * switches are read from the database instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    public static final String PREFIX = "feature.";
    public static final String CHAT = PREFIX + "chat.enabled";

    static final String CACHE_KEY = "config:feature-flags";
    static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final ParameterRepository parameterRepository;
    private final RedisUtil redisUtil;

    public boolean isEnabled(String key) {
        return Boolean.TRUE.equals(flags().get(key));
    }

    public boolean isChatEnabled() {
        return isEnabled(CHAT);
    }

    /** For the chat endpoints: 403 CHAT_DISABLED while the admin has chat switched off. */
    public void requireChatEnabled() {
        if (!isChatEnabled()) {
            throw new BusinessException(GlobalMessage.CHAT_DISABLED, HttpStatus.FORBIDDEN);
        }
    }

    /** Drops the cached switches once the surrounding transaction commits (right away when there is none). */
    public void evictAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict();
                }
            });
        } else {
            evict();
        }
    }

    public static boolean isFeatureFlag(String key) {
        return key != null && key.startsWith(PREFIX);
    }

    /**
     * The value to store for an admin create or update: {@code true} or {@code false} in lower case for a switch, the
     * value as given for any other parameter. 400 INVALID_PARAMETER for a switch with anything else.
     */
    public static String normalizeValue(String key, String value) {
        if (!isFeatureFlag(key) || value == null) {
            return value;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("true") && !normalized.equals("false")) {
            throw new BusinessException(GlobalMessage.INVALID_PARAMETER, HttpStatus.BAD_REQUEST, key);
        }
        return normalized;
    }

    Map<String, Boolean> flags() {
        try {
            if (redisUtil.get(CACHE_KEY) instanceof Map<?, ?> cached) {
                Map<String, Boolean> flags = new HashMap<>();
                cached.forEach((k, v) -> flags.put(String.valueOf(k), Boolean.TRUE.equals(v)));
                return flags;
            }
        } catch (RuntimeException e) {
            log.warn("FEATURE.FLAGS.CACHE.READ.FAILED message={}", e.getMessage());
        }

        Map<String, Boolean> flags = new HashMap<>();
        for (ParameterEntity p : parameterRepository.findByParamKeyStartingWith(PREFIX)) {
            flags.put(p.getParamKey(), "true".equalsIgnoreCase(p.getParamValue() == null ? "" : p.getParamValue().trim()));
        }
        try {
            redisUtil.set(CACHE_KEY, flags, CACHE_TTL.toSeconds());
        } catch (RuntimeException e) {
            log.warn("FEATURE.FLAGS.CACHE.WRITE.FAILED message={}", e.getMessage());
        }
        return flags;
    }

    private void evict() {
        try {
            redisUtil.delete(CACHE_KEY);
            log.info("FEATURE.FLAGS.CACHE.EVICTED");
        } catch (RuntimeException e) {
            // the TTL still refreshes it, just later
            log.warn("FEATURE.FLAGS.CACHE.EVICT.FAILED message={}", e.getMessage());
        }
    }
}
