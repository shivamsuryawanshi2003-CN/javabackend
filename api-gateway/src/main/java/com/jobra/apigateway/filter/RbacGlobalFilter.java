package com.jobra.apigateway.filter;

import com.jobra.apigateway.audit.GatewayAuditService;
import com.jobra.apigateway.config.GatewaySecurityPathProperties;
import com.jobra.apigateway.security.GatewayJwtValidator;
import com.jobra.apigateway.web.GatewayProblemJsonWriter;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Set;

/**
 * Enforces JWT + RBAC at the gateway. Must use the same {@code jwt.secret} as authservice.
 */
@Component
public class RbacGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final Set<String> KNOWN_ROLES = Set.of("END_USER", "ADMIN", "SUPER_ADMIN");

    private final GatewaySecurityPathProperties securityPaths;
    private final GatewayJwtValidator jwtValidator;
    private final GatewayAuditService auditService;
    private final GatewayProblemJsonWriter problemJsonWriter;

    public RbacGlobalFilter(GatewaySecurityPathProperties securityPaths,
                            GatewayJwtValidator jwtValidator,
                            GatewayAuditService auditService,
                            GatewayProblemJsonWriter problemJsonWriter) {
        this.securityPaths = securityPaths;
        this.jwtValidator = jwtValidator;
        this.auditService = auditService;
        this.problemJsonWriter = problemJsonWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        String path = request.getPath().pathWithinApplication().value();
        HttpMethod method = request.getMethod() != null ? request.getMethod() : HttpMethod.GET;

        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String token = resolveToken(request);
        if (token == null) {
            scheduleAudit("anonymous", path, method.name(), "UNAUTHORIZED");
            return problemJsonWriter.write(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Valid access token required",
                    "UNAUTHORIZED"
            );
        }

        Claims claims = jwtValidator.parseAndValidate(token);
        if (claims == null) {
            scheduleAudit("anonymous", path, method.name(), "UNAUTHORIZED");
            return problemJsonWriter.write(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Invalid or expired access token",
                    "INVALID_TOKEN"
            );
        }

        String email = jwtValidator.extractEmail(claims);
        String role = jwtValidator.extractRole(claims);
        if (role == null || role.isBlank()) {
            role = "END_USER";
        }

        if (!KNOWN_ROLES.contains(role)) {
            scheduleAudit(email != null ? email : "anonymous", path, method.name(), "INVALID_ROLE");
            return problemJsonWriter.write(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Invalid or unknown role in token",
                    "INVALID_ROLE"
            );
        }

        if (MATCHER.match(securityPaths.getAdminPattern(), path)) {
            if (!Set.of("ADMIN", "SUPER_ADMIN").contains(role)) {
                scheduleAudit(email, path, method.name(), "ACCESS_DENIED");
                return problemJsonWriter.write(
                        exchange,
                        HttpStatus.FORBIDDEN,
                        "Forbidden",
                        "Insufficient role for this resource",
                        "FORBIDDEN"
                );
            }
        }

        return chain.filter(exchange);
    }

    private void scheduleAudit(String principal, String path, String method, String reason) {
        Schedulers.boundedElastic().schedule(() ->
                auditService.logAccessDenial(principal, path, method, reason));
    }

    private boolean isPublic(String path) {
        // Server-to-server endpoints: authservice validates X-Internal-Key; gateway must not require JWT.
        if (path.startsWith("/api/auth/internal/")) {
            return true;
        }
        for (String pattern : securityPaths.getPublicPatterns()) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveToken(ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        if (request.getCookies().containsKey("token")) {
            var c = request.getCookies().getFirst("token");
            if (c != null) {
                return c.getValue();
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }
}
