package com.jobra.authservice.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void store(String jti, String email, long ttlSeconds) {
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, email, Duration.ofSeconds(ttlSeconds));
    }

    public boolean exists(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }

    public void delete(String jti) {
        redisTemplate.delete(KEY_PREFIX + jti);
    }
}
