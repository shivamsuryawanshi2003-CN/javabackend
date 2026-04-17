package com.jobra.apigateway.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Allowed browser {@code Origin} values — must stay aligned with {@code spring.cloud.gateway.globalcors} in
 * {@code application.yml}. Used when responses bypass the gateway CORS merge (e.g. problem JSON from {@link GatewayProblemJsonWriter}).
 */
@Component
public class GatewayCorsHeaders {

    private final List<String> allowedOrigins;

    public GatewayCorsHeaders(
            @Value("${gateway.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}") String raw) {
        String cleaned = raw == null ? "" : raw.replace("\"", "").trim();
        this.allowedOrigins = Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Origins permitted to call the gateway from a browser (credentials / cookies).
     */
    public List<String> getAllowedOrigins() {
        return Collections.unmodifiableList(allowedOrigins);
    }

    public void apply(ServerWebExchange exchange, ServerHttpResponse response) {
        String origin = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (origin == null || origin.isBlank()) {
            return;
        }
        String o = origin.trim();
        for (String allowed : allowedOrigins) {
            if (allowed.equals(o)) {
                response.getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, o);
                response.getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
                response.getHeaders().add(HttpHeaders.VARY, HttpHeaders.ORIGIN);
                return;
            }
        }
    }
}
