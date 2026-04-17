package com.jobra.authservice.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Tracks up to {@link #MAX_CONCURRENT_SESSIONS} refresh-token sessions (JTIs) per user.
 */
@Service
public class UserSessionService {

    static final int MAX_CONCURRENT_SESSIONS = 5;
    private static final String SESSION_LIST_PREFIX = "auth:user-sessions:";

    private final StringRedisTemplate redisTemplate;
    private final RefreshTokenStore refreshTokenStore;

    public UserSessionService(StringRedisTemplate redisTemplate, RefreshTokenStore refreshTokenStore) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenStore = refreshTokenStore;
    }

    public void registerNewSession(String email, String jti, long refreshTtlSeconds) {
        String key = SESSION_LIST_PREFIX + email;
        redisTemplate.opsForList().leftPush(key, jti);
        Long size;
        while ((size = redisTemplate.opsForList().size(key)) != null && size > MAX_CONCURRENT_SESSIONS) {
            String evicted = redisTemplate.opsForList().rightPop(key);
            if (evicted != null) {
                refreshTokenStore.delete(evicted);
            }
        }
        refreshTokenStore.store(jti, email, refreshTtlSeconds);
    }

    public boolean isSessionActive(String email, String jti) {
        String key = SESSION_LIST_PREFIX + email;
        List<String> tokens = redisTemplate.opsForList().range(key, 0, -1);
        if (tokens == null) {
            return false;
        }
        return tokens.contains(jti);
    }

    public void removeSession(String email, String jti) {
        redisTemplate.opsForList().remove(SESSION_LIST_PREFIX + email, 1, jti);
        refreshTokenStore.delete(jti);
    }
}
