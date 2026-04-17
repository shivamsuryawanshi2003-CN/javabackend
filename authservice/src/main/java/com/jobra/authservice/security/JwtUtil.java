package com.jobra.authservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    public static final String ACCESS_TOKEN = "access";
    public static final String REFRESH_TOKEN = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration-minutes:15}")
    private long accessTokenExpirationMinutes;

    @Value("${jwt.refresh-token-expiration-days:7}")
    private long refreshTokenExpirationDays;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateAccessToken(String email, String role, String subscription, String jti) {
        return buildToken(email, role, subscription, jti, ACCESS_TOKEN,
                Duration.ofMinutes(accessTokenExpirationMinutes).toMillis());
    }

    public String generateRefreshToken(String email, String role, String subscription, String jti) {
        return buildToken(email, role, subscription, jti, REFRESH_TOKEN,
                Duration.ofDays(refreshTokenExpirationDays).toMillis());
    }

    public String generateToken(String email, String role, String subscription) {
        return generateAccessToken(email, role, subscription, UUID.randomUUID().toString());
    }

    private String buildToken(String email, String role, String subscription, String jti, String tokenType, long expirationMillis) {
        return Jwts.builder()
                .setSubject(email)
                .setId(jti)
                .claim("role", role)
                .claim("subscription", subscription)
                .claim("type", tokenType)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public String extractSubscription(String token) {
        return getClaims(token).get("subscription", String.class);
    }

    public String extractJti(String token) {
        return getClaims(token).getId();
    }

    public String extractTokenType(String token) {
        return getClaims(token).get("type", String.class);
    }

    public long getAccessTokenExpirationSeconds() {
        return Duration.ofMinutes(accessTokenExpirationMinutes).toSeconds();
    }

    public long getRefreshTokenExpirationSeconds() {
        return Duration.ofDays(refreshTokenExpirationDays).toSeconds();
    }
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false; // invalid token
        }
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token) && REFRESH_TOKEN.equals(extractTokenType(token));
    }

}
