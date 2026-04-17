package com.jobra.authservice.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Service
public class LoginAttemptService {

    static final int MAX_FAILURES_BEFORE_LOCKOUT = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    /** Rolling window in which failed attempts count toward lockout (spec: hourly-style limit). */
    private static final Duration FAILURE_WINDOW = Duration.ofHours(1);

    private static final String FAIL_PREFIX = "auth:loginfail:";
    private static final String LOCK_PREFIX = "auth:loginlock:";

    private final StringRedisTemplate redisTemplate;

    public LoginAttemptService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void assertNotLocked(String email) {
        String lockKey = LOCK_PREFIX + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed login attempts. Try again in 15 minutes."
            );
        }
    }

    public void recordFailedAttempt(String email) {
        String failKey = FAIL_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(failKey, FAILURE_WINDOW);
        }
        if (count != null && count >= MAX_FAILURES_BEFORE_LOCKOUT) {
            redisTemplate.opsForValue().set(LOCK_PREFIX + email, "1", LOCKOUT_DURATION);
            redisTemplate.delete(failKey);
        }
    }

    public void clearFailures(String email) {
        redisTemplate.delete(FAIL_PREFIX + email);
    }
}
