package com.jobra.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class GatewayJwtValidator {

    public static final String ACCESS_TOKEN = "access";

    @Value("${jwt.secret}")
    private String secret;

    private Key signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims parseAndValidate(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            if (claims.getExpiration() != null && claims.getExpiration().before(new Date())) {
                return null;
            }
            if (!ACCESS_TOKEN.equals(claims.get("type", String.class))) {
                return null;
            }
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    public String extractEmail(Claims claims) {
        return claims.getSubject();
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }
}
