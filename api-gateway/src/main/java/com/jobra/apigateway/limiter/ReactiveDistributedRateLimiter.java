package com.jobra.apigateway.limiter;

import com.jobra.apigateway.config.GatewayRateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window request counting in Redis (one counter per client key per time bucket).
 */
@Service
public class ReactiveDistributedRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ReactiveDistributedRateLimiter.class);

    private final ObjectProvider<ReactiveStringRedisTemplate> redis;
    private final GatewayRateLimitProperties properties;

    public ReactiveDistributedRateLimiter(ObjectProvider<ReactiveStringRedisTemplate> redis,
                                          GatewayRateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public Mono<RateOutcome> tryAcquire(String scopeName, String clientKey, int maxPerWindow) {
        if (!properties.isEnabled()) {
            return Mono.just(RateOutcome.ok());
        }
        ReactiveStringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            log.warn("ReactiveStringRedisTemplate not available; rate limiting skipped");
            return Mono.just(RateOutcome.ok());
        }

        long window = Instant.now().getEpochSecond() / Math.max(1, properties.getWindowSeconds());
        String redisKey = properties.getKeyPrefix() + ":v1:" + scopeName + ":" + clientKey + ":" + window;
        Duration ttl = Duration.ofSeconds((long) properties.getWindowSeconds() * 2L);

        return template.opsForValue().increment(redisKey)
                .flatMap(count -> {
                    if (count != null && count == 1L) {
                        return template.expire(redisKey, ttl).thenReturn(count);
                    }
                    return Mono.justOrEmpty(count);
                })
                .switchIfEmpty(Mono.just(1L))
                .map(count -> {
                    if (count <= maxPerWindow) {
                        return RateOutcome.ok();
                    }
                    long secIntoWindow = Instant.now().getEpochSecond() % Math.max(1, properties.getWindowSeconds());
                    long retryAfter = Math.max(1, properties.getWindowSeconds() - secIntoWindow);
                    return RateOutcome.limited(retryAfter);
                })
                .onErrorResume(ex -> {
                    if (properties.isFailOpen()) {
                        log.warn("Rate limit check failed (fail-open): {}", ex.toString());
                        return Mono.just(RateOutcome.ok());
                    }
                    log.error("Rate limit check failed (fail-closed)", ex);
                    return Mono.just(RateOutcome.limited(properties.getWindowSeconds()));
                });
    }

    public record RateOutcome(boolean permitted, long retryAfterSeconds) {
        public static RateOutcome ok() {
            return new RateOutcome(true, 0);
        }

        public static RateOutcome limited(long retryAfterSeconds) {
            return new RateOutcome(false, retryAfterSeconds);
        }
    }
}
