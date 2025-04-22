package com.rizkysiregar.feedzmore.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public RedisUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Set value with optional expiration (in seconds)
    public void set(String key, Object value, long timeoutSeconds) {
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(timeoutSeconds));
    }

    // Set value with no expiration
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    // Get value
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // Delete key
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    // Check if key exists
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // Set TTL (Time to Live) on a key
    public void expire(String key, long timeoutSeconds) {
        redisTemplate.expire(key, Duration.ofSeconds(timeoutSeconds));
    }

    // Get remaining TTL
    public Long getExpire(String key) {
        return redisTemplate.getExpire(key);
    }
}
