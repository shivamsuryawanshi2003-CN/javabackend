package com.jobra.authservice.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class MfaPendingStore {

    private static final String PREFIX = "auth:mfa-pending:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public MfaPendingStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void put(String email, String secret) {
        redisTemplate.opsForValue().set(PREFIX + email, secret, TTL);
    }

    public String get(String email) {
        return redisTemplate.opsForValue().get(PREFIX + email);
    }

    public void delete(String email) {
        redisTemplate.delete(PREFIX + email);
    }
}
