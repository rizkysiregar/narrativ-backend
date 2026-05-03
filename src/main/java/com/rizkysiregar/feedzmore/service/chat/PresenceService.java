package com.rizkysiregar.feedzmore.service.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PresenceService {
    private final StringRedisTemplate redis;
    // Key patterns
    private static final String ONLINE_KEY = "presence:online:%d";
    private static final String TYPING_KEY = "presence:typing:%s:%d"; // conversationId:userId

    // --- Online / Offline ---
    public void markOnline(UUID userId) {
        String key = ONLINE_KEY.formatted(userId);
        redis.opsForValue().set(key, "1", Duration.ofSeconds(30));
        // Android should heartbeat every 20s to keep this alive
    }

    public void markOffline(Long userId) {
        redis.delete(ONLINE_KEY.formatted(userId));
    }

    public boolean isOnline(Long userId) {
        return Boolean.TRUE.equals(redis.hasKey(ONLINE_KEY.formatted(userId)));
    }

    // --- Typing indicators ---
    public void setTyping(UUID conversationId, UUID userId, boolean typing) {
        String key = TYPING_KEY.formatted(conversationId, userId);
        if (typing) {
            redis.opsForValue().set(key, "1", Duration.ofSeconds(5));
            // Auto-expires — no need to send "stop typing" explicitly
        } else {
            redis.delete(key);
        }
    }

    public boolean isTyping(UUID conversationId, Long userId) {
        return Boolean.TRUE.equals(redis.hasKey(TYPING_KEY.formatted(conversationId, userId)));
    }


}
