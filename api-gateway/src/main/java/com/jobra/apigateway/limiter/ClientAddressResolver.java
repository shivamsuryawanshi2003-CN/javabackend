package com.jobra.apigateway.limiter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves a stable client key for rate limiting behind reverse proxies / load balancers.
 */
@Component
public class ClientAddressResolver {

    private static final String UNKNOWN = "unknown";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    public String resolveClientKey(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst(X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
            if (!first.isEmpty()) {
                return sanitize(first);
            }
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return sanitize(request.getRemoteAddress().getAddress().getHostAddress());
        }
        return UNKNOWN;
    }

    private static String sanitize(String ip) {
        return ip.replace(':', '_');
    }
}
