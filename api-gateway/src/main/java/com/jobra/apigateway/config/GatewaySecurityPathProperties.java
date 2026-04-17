package com.jobra.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Public routes that skip JWT checks at the gateway (must stay aligned with authservice security).
 */
@Validated
@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityPathProperties {

    /**
     * Ant-style patterns, e.g. {@code /oauth2/**}.
     */
    private List<String> publicPatterns = new ArrayList<>(List.of(
            "/api/auth/register",
            "/api/auth/verify-otp",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/resend-otp",
            "/api/auth/password-reset/request",
            "/api/auth/password-reset/confirm",
            "/api/auth/session",
            "/api/auth/users/by-email",
            "/api/auth/internal/subscription",
            "/api/auth/internal/role",
            "/api/auth/mfa/enroll/start",
            "/api/auth/mfa/enroll/confirm",
            "/api/auth/oauth/mfa/verify",
            "/oauth2/**",
            "/login/**"
    ));

    private String adminPattern = "/api/admin/**";

    public List<String> getPublicPatterns() {
        return publicPatterns;
    }

    public void setPublicPatterns(List<String> publicPatterns) {
        this.publicPatterns = publicPatterns;
    }

    public String getAdminPattern() {
        return adminPattern;
    }

    public void setAdminPattern(String adminPattern) {
        this.adminPattern = adminPattern;
    }
}
