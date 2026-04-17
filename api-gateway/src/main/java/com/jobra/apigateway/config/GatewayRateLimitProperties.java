package com.jobra.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Distributed per-IP rate limits (Redis). Tune per environment.
 */
@Validated
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class GatewayRateLimitProperties {

    private boolean enabled = true;

    /**
     * If Redis is unavailable, allow traffic (availability over strict limiting).
     */
    private boolean failOpen = true;

    /**
     * Redis key prefix.
     */
    private String keyPrefix = "gw:rl";

    /**
     * Window length for fixed-window counters (seconds). Keys expire after 2x window.
     */
    private int windowSeconds = 60;

    /**
     * Default max requests per window per client key (IP), unless a rule overrides.
     */
    private int defaultMaxPerWindow = 300;

    private List<Rule> rules = new ArrayList<>(List.of(
            new Rule(List.of(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/verify-otp",
                    "/api/auth/resend-otp",
                    "/api/auth/password-reset/request",
                    "/api/auth/password-reset/confirm",
                    "/api/auth/oauth/mfa/verify"
            ), 40, "auth-sensitive"),
            new Rule(List.of("/api/auth/mfa/**"), 20, "mfa"),
            new Rule(List.of("/oauth2/**", "/login/**"), 120, "oauth")
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getDefaultMaxPerWindow() {
        return defaultMaxPerWindow;
    }

    public void setDefaultMaxPerWindow(int defaultMaxPerWindow) {
        this.defaultMaxPerWindow = defaultMaxPerWindow;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static class Rule {
        private List<String> patterns = new ArrayList<>();
        private int maxPerWindow = 100;
        private String name = "custom";

        public Rule() {
        }

        public Rule(List<String> patterns, int maxPerWindow, String name) {
            this.patterns = patterns;
            this.maxPerWindow = maxPerWindow;
            this.name = name;
        }

        public List<String> getPatterns() {
            return patterns;
        }

        public void setPatterns(List<String> patterns) {
            this.patterns = patterns;
        }

        public int getMaxPerWindow() {
            return maxPerWindow;
        }

        public void setMaxPerWindow(int maxPerWindow) {
            this.maxPerWindow = maxPerWindow;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
