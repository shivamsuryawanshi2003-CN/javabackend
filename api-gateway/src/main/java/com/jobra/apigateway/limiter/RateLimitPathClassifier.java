package com.jobra.apigateway.limiter;

import com.jobra.apigateway.config.GatewayRateLimitProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * Maps a request path to the first matching rule (order matters in configuration).
 */
@Component
public class RateLimitPathClassifier {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final GatewayRateLimitProperties properties;

    public RateLimitPathClassifier(GatewayRateLimitProperties properties) {
        this.properties = properties;
    }

    public int maxForPath(String path) {
        for (GatewayRateLimitProperties.Rule rule : properties.getRules()) {
            for (String pattern : rule.getPatterns()) {
                if (matcher.match(pattern, path)) {
                    return rule.getMaxPerWindow();
                }
            }
        }
        return properties.getDefaultMaxPerWindow();
    }

    public String scopeForPath(String path) {
        for (GatewayRateLimitProperties.Rule rule : properties.getRules()) {
            for (String pattern : rule.getPatterns()) {
                if (matcher.match(pattern, path)) {
                    return rule.getName();
                }
            }
        }
        return "default";
    }
}
