package com.jobra.authservice.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Limits OTP email sends and verification attempts per client IP (rolling 1-hour window).
 * Spec: at most {@value #MAX_EVENTS_PER_IP_PER_HOUR} OTP-related events per IP per hour.
 */
@Service
public class OtpIpRateLimiter {

    static final int MAX_EVENTS_PER_IP_PER_HOUR = 3;
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final String KEY_PREFIX = "auth:otp:ip:";

    private final StringRedisTemplate redisTemplate;

    public OtpIpRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkAndRecord(String clientIp) {
        if (clientIp == null || clientIp.isBlank() || "unknown".equalsIgnoreCase(clientIp)) {
            return;
        }
        String key = KEY_PREFIX + clientIp;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
        if (count != null && count > MAX_EVENTS_PER_IP_PER_HOUR) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many OTP requests from this network. Try again in up to 1 hour."
            );
        }
    }
}
