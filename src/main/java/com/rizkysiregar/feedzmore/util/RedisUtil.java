package com.rizkysiregar.feedzmore.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
public class RedisUtil implements CommandLineRunner {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

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

    // Set value only if the key does not exist yet (atomic SETNX + TTL).
    // Returns true when this caller created the key.
    public boolean setIfAbsent(String key, Object value, long timeoutSeconds) {
        Boolean created = redisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(timeoutSeconds));
        return Boolean.TRUE.equals(created);
    }

    // Atomically increment a counter (INCR). A missing key starts from 0, so the first call returns 1.
    public long increment(String key) {
        Long value = redisTemplate.opsForValue().increment(key);
        return value == null ? 0L : value;
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

    @Override
    public void run(String... args) throws Exception {
        log.info("## [Redis Service run on: ] " + redisHost + ", " + redisPort);
    }
}
