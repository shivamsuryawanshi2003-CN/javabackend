package com.jobra.authservice.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class OAuthMfaChallengeStore {

    private static final String PREFIX = "auth:oauth-mfa:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public OAuthMfaChallengeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void put(String token, String email) {
        redisTemplate.opsForValue().set(PREFIX + token, email, TTL);
    }

    public String consume(String token) {
        String key = PREFIX + token;
        String email = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        return email;
    }
}
