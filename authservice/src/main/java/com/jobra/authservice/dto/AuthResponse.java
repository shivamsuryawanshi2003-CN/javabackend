package com.jobra.authservice.dto;

public class AuthResponse {

    private final String accessToken;
    private final long expiresIn;
    private final String tokenType;

    public AuthResponse(String accessToken, long expiresIn, String tokenType) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.tokenType = tokenType;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public String getTokenType() {
        return tokenType;
    }
}
