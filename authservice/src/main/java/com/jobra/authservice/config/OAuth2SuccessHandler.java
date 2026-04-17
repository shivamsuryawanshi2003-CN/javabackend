package com.jobra.authservice.config;

import com.jobra.authservice.entity.Role;
import com.jobra.authservice.entity.Subscription;
import com.jobra.authservice.entity.User;
import com.jobra.authservice.oauth.OAuth2UserAttributes;
import com.jobra.authservice.repository.UserRepository;
import com.jobra.authservice.security.JwtUtil;
import com.jobra.authservice.service.OAuthMfaChallengeStore;
import com.jobra.authservice.service.UserSessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final UserSessionService userSessionService;
    private final OAuthMfaChallengeStore oauthMfaChallengeStore;
    private final String frontendBaseUrl;

    public OAuth2SuccessHandler(
            UserRepository userRepository,
            JwtUtil jwtUtil,
            UserSessionService userSessionService,
            OAuthMfaChallengeStore oauthMfaChallengeStore,
            @Value("${app.frontend-url:http://localhost:3000}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.userSessionService = userSessionService;
        this.oauthMfaChallengeStore = oauthMfaChallengeStore;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/$", "");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        OAuth2User principal = oauthToken.getPrincipal();

        String email = OAuth2UserAttributes.email(registrationId, principal);
        String name = OAuth2UserAttributes.name(registrationId, principal);
        if (email == null || email.isBlank()) {
            response.sendRedirect(frontendBaseUrl + "/login?error=oauth_email");
            return;
        }
        String provider = registrationId != null ? registrationId.toUpperCase() : "OAUTH";

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail(email);
                    u.setName(name != null ? name : email);
                    u.setProvider(provider);
                    u.setRole(Role.END_USER);
                    u.setSubscription(Subscription.FREE);
                    u.setActive(true);
                    return userRepository.save(u);
                });

        if (!user.isActive()) {
            user.setActive(true);
            userRepository.save(user);
        }

        Role r = user.getRole() != null ? user.getRole() : Role.END_USER;
        if (isPrivileged(r) && !user.isMfaEnabled()) {
            String q = URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8);
            response.sendRedirect(frontendBaseUrl + "/login?error=mfa_enroll_required&email=" + q);
            return;
        }
        if (isPrivileged(r)) {
            String challengeToken = UUID.randomUUID().toString();
            oauthMfaChallengeStore.put(challengeToken, user.getEmail());
            String emailParam = URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8);
            String challengeParam = URLEncoder.encode(challengeToken, StandardCharsets.UTF_8);
            response.sendRedirect(frontendBaseUrl + "/login?mfa=1&oauth=1&email=" + emailParam + "&challenge=" + challengeParam);
            return;
        }

        String subscription = user.getSubscription() != null
                ? user.getSubscription().name()
                : "FREE";
        String role = user.getRole() != null ? user.getRole().name() : Role.END_USER.name();
        boolean isProd = request.isSecure();

        String jti = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), role, subscription, jti);
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), role, subscription, jti);
        userSessionService.registerNewSession(user.getEmail(), jti, jwtUtil.getRefreshTokenExpirationSeconds());

        Cookie accessCookie = new Cookie("token", accessToken);
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(isProd);
        accessCookie.setPath("/");
        accessCookie.setMaxAge((int) jwtUtil.getAccessTokenExpirationSeconds());
        accessCookie.setAttribute("SameSite", isProd ? "None" : "Lax");
        response.addCookie(accessCookie);

        Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(isProd);
        refreshCookie.setPath("/api/auth");
        refreshCookie.setMaxAge((int) jwtUtil.getRefreshTokenExpirationSeconds());
        refreshCookie.setAttribute("SameSite", isProd ? "None" : "Lax");
        response.addCookie(refreshCookie);
        response.sendRedirect(frontendBaseUrl + "/dashboard");
    }

    private static boolean isPrivileged(Role role) {
        return role == Role.ADMIN || role == Role.SUPER_ADMIN;
    }
}
