package com.jobra.authservice.service;

import com.jobra.authservice.dto.LoginRequest;
import com.jobra.authservice.dto.MfaEnrollConfirmRequest;
import com.jobra.authservice.dto.MfaEnrollStartRequest;
import com.jobra.authservice.dto.OAuthMfaVerifyRequest;
import com.jobra.authservice.dto.OtpRequest;
import com.jobra.authservice.dto.PasswordResetConfirmRequest;
import com.jobra.authservice.dto.PasswordResetRequest;
import com.jobra.authservice.dto.RegisterRequest;
import com.jobra.authservice.dto.InternalRoleUpdateRequest;
import com.jobra.authservice.dto.SubscriptionUpdateRequest;
import com.jobra.authservice.entity.Role;
import com.jobra.authservice.entity.Subscription;
import com.jobra.authservice.entity.User;
import com.jobra.authservice.event.RegistrationNotificationEvent;
import com.jobra.authservice.repository.UserRepository;
import com.jobra.authservice.security.JwtUtil;
import com.jobra.authservice.web.ClientIpResolver;
import dev.samstevens.totp.exceptions.QrGenerationException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final String ACCESS_COOKIE_NAME = "token";
    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final int PASSWORD_RESET_OTP_TTL_MINUTES = 10;
    private static final int MAX_PASSWORD_RESET_OTP_ATTEMPTS = 3;
    private final String internalApiKey;
    private final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final UserSessionService userSessionService;
    private final NotificationEventProducer notificationEventProducer;
    private final LoginAttemptService loginAttemptService;
    private final MfaService mfaService;
    private final MfaPendingStore mfaPendingStore;
    private final OtpIpRateLimiter otpIpRateLimiter;
    private final EmailService emailService;
    private final OAuthMfaChallengeStore oauthMfaChallengeStore;

    public AuthService(UserRepository userRepository,
                       AuthenticationManager authenticationManager,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       OtpService otpService,
                       UserSessionService userSessionService,
                       NotificationEventProducer notificationEventProducer,
                       LoginAttemptService loginAttemptService,
                       MfaService mfaService,
                       MfaPendingStore mfaPendingStore,
                       OtpIpRateLimiter otpIpRateLimiter,
                       EmailService emailService,
                       OAuthMfaChallengeStore oauthMfaChallengeStore,
                       @Value("${auth.internal-api-key:}") String internalApiKey) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.otpService = otpService;
        this.userSessionService = userSessionService;
        this.notificationEventProducer = notificationEventProducer;
        this.loginAttemptService = loginAttemptService;
        this.mfaService = mfaService;
        this.mfaPendingStore = mfaPendingStore;
        this.otpIpRateLimiter = otpIpRateLimiter;
        this.emailService = emailService;
        this.oauthMfaChallengeStore = oauthMfaChallengeStore;
        this.internalApiKey = internalApiKey;
    }

    private static boolean isPrivileged(Role role) {
        return role == Role.ADMIN || role == Role.SUPER_ADMIN;
    }

    public Map<String, Object> register(RegisterRequest request, HttpServletRequest httpRequest) {
        String clientIp = ClientIpResolver.resolve(httpRequest);
        Optional<User> existingUser = userRepository.findByEmail(request.getEmail());

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (user.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already registered");
            }
            user.setName(request.getName());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setRole(Role.END_USER);
            user.setSubscription(Subscription.FREE);
            userRepository.save(user);
            otpService.generateForUser(user, clientIp);
            notificationEventProducer.sendRegistrationEvent(
                    new RegistrationNotificationEvent(user.getEmail(), user.getName(), "REGISTER_OTP_RESENT"));
            return Map.of(
                    "message", "OTP sent to email",
                    "email", user.getEmail(),
                    "status", "INACTIVE"
            );
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setProvider("LOCAL");
        user.setRole(Role.END_USER);
        user.setSubscription(Subscription.FREE);
        user.setActive(false);
        userRepository.save(user);

        otpService.generateForUser(user, clientIp);
        notificationEventProducer.sendRegistrationEvent(
                new RegistrationNotificationEvent(user.getEmail(), user.getName(), "REGISTERED"));

        return Map.of(
                "message", "OTP sent to email",
                "email", user.getEmail(),
                "status", "INACTIVE"
        );
    }

    public Map<String, Object> verifyOtp(OtpRequest request) {
        User user = otpService.verify(request);
        return Map.of(
                "message", "Email verified successfully",
                "email", user.getEmail(),
                "status", "ACTIVE"
        );
    }

    public Map<String, Object> resendOtp(String email, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User already verified");
        }
        otpService.generateForUser(user, ClientIpResolver.resolve(httpRequest));
        notificationEventProducer.sendRegistrationEvent(
                new RegistrationNotificationEvent(user.getEmail(), user.getName(), "REGISTER_OTP_RESENT"));
        return Map.of(
                "message", "OTP sent again",
                "email", user.getEmail(),
                "status", "INACTIVE"
        );
    }

    public Map<String, Object> login(LoginRequest request, boolean secureRequest, HttpServletResponse response) {
        String email = request.getEmail();
        loginAttemptService.assertNotLocked(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    loginAttemptService.recordFailedAttempt(email);
                    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid credentials");
                });

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please verify your email using OTP");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (DisabledException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is inactive");
        } catch (BadCredentialsException ex) {
            loginAttemptService.recordFailedAttempt(email);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid credentials");
        }

        Role role = user.getRole() != null ? user.getRole() : Role.END_USER;
        if (isPrivileged(role) && !user.isMfaEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "MFA enrollment required. Set up two-factor authentication before signing in."
            );
        }

        if (isPrivileged(role) && user.isMfaEnabled()) {
            if (!mfaService.verifyTotp(user.getMfaSecret(), request.getMfaCode())) {
                loginAttemptService.recordFailedAttempt(email);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing MFA code");
            }
        }

        loginAttemptService.clearFailures(email);

        issueLoginTokens(user, secureRequest, response);

        return Map.of(
                "message", "Login successful",
                "expiresIn", jwtUtil.getAccessTokenExpirationSeconds(),
                "tokenType", "Bearer"
        );
    }

    public Map<String, Object> requestPasswordReset(PasswordResetRequest request, HttpServletRequest httpRequest) {
        Optional<User> maybeUser = userRepository.findByEmail(request.getEmail());
        if (maybeUser.isPresent()) {
            User user = maybeUser.get();
            if (user.isActive()) {
                otpIpRateLimiter.checkAndRecord(ClientIpResolver.resolve(httpRequest));
                String otp = String.valueOf(100000 + secureRandom.nextInt(900000));
                user.setPasswordResetOtp(passwordEncoder.encode(otp));
                user.setPasswordResetOtpExpiry(LocalDateTime.now().plusMinutes(PASSWORD_RESET_OTP_TTL_MINUTES));
                user.setPasswordResetOtpAttempts(0);
                userRepository.save(user);
                emailService.sendPasswordResetOtpEmail(user.getEmail(), otp);
            }
        }
        return Map.of("message", "If an account exists, a reset OTP has been sent");
    }

    public Map<String, Object> confirmPasswordReset(PasswordResetConfirmRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset OTP"));

        if (user.getPasswordResetOtp() == null || user.getPasswordResetOtpExpiry() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset OTP");
        }
        if (user.getPasswordResetOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset OTP expired");
        }
        if (user.getPasswordResetOtpAttempts() >= MAX_PASSWORD_RESET_OTP_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum reset OTP attempts exceeded");
        }
        if (!passwordEncoder.matches(request.getOtp(), user.getPasswordResetOtp())) {
            user.setPasswordResetOtpAttempts(user.getPasswordResetOtpAttempts() + 1);
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset OTP");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetOtp(null);
        user.setPasswordResetOtpExpiry(null);
        user.setPasswordResetOtpAttempts(0);
        userRepository.save(user);

        return Map.of("message", "Password reset successful");
    }

    /**
     * MFA enroll must verify the user. If the account was created via OAuth only, {@link User#getPassword()} is null —
     * in that case the first non-empty password provided here is stored (min 8 chars) so the same password can be used
     * for email/password login and for the confirm step.
     */
    private void verifyPasswordForMfaEnrollment(User user, String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        String stored = user.getPassword();
        if (stored == null || stored.isBlank()) {
            if (plainPassword.length() < 8) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "This account has no password (e.g. Google/Microsoft sign-in). Enter a new password (min 8 characters) to set it for MFA setup and future sign-in."
                );
            }
            user.setPassword(passwordEncoder.encode(plainPassword));
            userRepository.save(user);
            return;
        }
        if (!passwordEncoder.matches(plainPassword, stored)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid credentials");
        }
    }

    public Map<String, Object> mfaEnrollStart(MfaEnrollStartRequest request) throws QrGenerationException {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid credentials"));
        verifyPasswordForMfaEnrollment(user, request.getPassword());
        Role role = user.getRole() != null ? user.getRole() : Role.END_USER;
        if (!isPrivileged(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MFA enrollment applies to ADMIN and SUPER_ADMIN only");
        }
        if (user.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MFA is already enabled");
        }
        String secret = mfaService.generateSecret();
        mfaPendingStore.put(user.getEmail(), secret);
        String otpauthUri = mfaService.otpAuthUri(user.getEmail(), secret);
        byte[] png = mfaService.qrPng(user.getEmail(), secret);
        return Map.of(
                "otpauthUri", otpauthUri,
                "secret", secret,
                "qrImageDataUrl", mfaService.qrImageDataUrl(png)
        );
    }

    public Map<String, Object> mfaEnrollConfirm(MfaEnrollConfirmRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid credentials"));
        Role role = user.getRole() != null ? user.getRole() : Role.END_USER;
        if (!isPrivileged(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MFA enrollment applies to ADMIN and SUPER_ADMIN only");
        }
        String pending = mfaPendingStore.get(user.getEmail());
        if (pending == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending MFA enrollment; call /api/auth/mfa/enroll/start first");
        }
        verifyPasswordForMfaEnrollment(user, request.getPassword());
        if (!mfaService.verifyTotp(pending, request.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification code");
        }
        user.setMfaSecret(pending);
        user.setMfaEnabled(true);
        userRepository.save(user);
        mfaPendingStore.delete(user.getEmail());
        return Map.of("message", "MFA enabled", "email", user.getEmail());
    }

    public Map<String, Object> verifyOAuthMfa(OAuthMfaVerifyRequest request,
                                              boolean secureRequest,
                                              HttpServletResponse response) {
        String challengeEmail = oauthMfaChallengeStore.consume(request.getChallengeToken());
        if (challengeEmail == null || !challengeEmail.equalsIgnoreCase(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OAuth MFA challenge");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid credentials"));
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please verify your email using OTP");
        }
        Role role = user.getRole() != null ? user.getRole() : Role.END_USER;
        if (!isPrivileged(role) || !user.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MFA verification is not required");
        }
        if (!mfaService.verifyTotp(user.getMfaSecret(), request.getMfaCode())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing MFA code");
        }

        issueLoginTokens(user, secureRequest, response);
        return Map.of(
                "message", "Login successful",
                "expiresIn", jwtUtil.getAccessTokenExpirationSeconds(),
                "tokenType", "Bearer"
        );
    }

    public Map<String, Object> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readCookie(request, REFRESH_COOKIE_NAME);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        String jti = jwtUtil.extractJti(refreshToken);
        if (!userSessionService.isSessionActive(jwtUtil.extractEmail(refreshToken), jti)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
        }

        String email = jwtUtil.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is inactive");
        }

        String accessToken = jwtUtil.generateAccessToken(
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : Role.END_USER.name(),
                user.getSubscription() != null ? user.getSubscription().name() : Subscription.FREE.name(),
                UUID.randomUUID().toString()
        );

        boolean secure = request.isSecure();
        addAccessTokenCookie(response, accessToken, secure);

        return Map.of(
                "expiresIn", jwtUtil.getAccessTokenExpirationSeconds(),
                "tokenType", "Bearer",
                "message", "Token refreshed"
        );
    }

    public void logout(HttpServletRequest request, HttpServletResponse response, boolean secureRequest) {
        String refreshToken = readCookie(request, REFRESH_COOKIE_NAME);
        if (refreshToken != null && jwtUtil.validateRefreshToken(refreshToken)) {
            String jti = jwtUtil.extractJti(refreshToken);
            String email = jwtUtil.extractEmail(refreshToken);
            userSessionService.removeSession(email, jti);
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        clearCookie(response, REFRESH_COOKIE_NAME, "/api/auth", secureRequest);
        clearCookie(response, ACCESS_COOKIE_NAME, "/", secureRequest);
        clearCookie(response, "JSESSIONID", "/", secureRequest);
    }

    /**
     * Soft session for SPAs: always returns 200 — {@code authenticated: false} when logged out (no 401 in browser console).
     */
    public Map<String, Object> sessionUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Map.of("authenticated", false);
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> {
                    String roleName = user.getRole() != null ? user.getRole().name() : Role.END_USER.name();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("authenticated", true);
                    m.put("email", user.getEmail());
                    m.put("name", user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail());
                    m.put("role", roleName);
                    return m;
                })
                .orElseGet(() -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("authenticated", true);
                    m.put("email", email);
                    m.put("name", email);
                    m.put("role", Role.END_USER.name());
                    return m;
                });
    }

    /**
     * Current session user for {@code GET /api/me} (JWT cookie or Bearer).
     */
    public Map<String, Object> currentUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .map(user -> {
                    String roleName = user.getRole() != null ? user.getRole().name() : Role.END_USER.name();
                    return Map.<String, Object>of(
                            "email", user.getEmail(),
                            "name", user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail(),
                            "role", roleName
                    );
                })
                .orElse(Map.of(
                        "email", email,
                        "name", email,
                        "role", Role.END_USER.name()
                ));
    }

    public Map<String, Object> getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "active", user.isActive()
        );
    }

    public Map<String, Object> updateSubscription(String providedInternalKey, SubscriptionUpdateRequest request) {
        requireValidInternalKey(providedInternalKey);

        Subscription subscription;
        try {
            subscription = Subscription.valueOf(request.getPlan().trim().toUpperCase());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid subscription plan: " + request.getPlan());
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setSubscription(subscription);
        userRepository.save(user);
        return Map.of(
                "message", "Subscription updated",
                "email", user.getEmail(),
                "subscription", user.getSubscription().name()
        );
    }

    public Map<String, Object> updateRoleInternal(String providedInternalKey, InternalRoleUpdateRequest request) {
        requireValidInternalKey(providedInternalKey);

        Role newRole;
        try {
            newRole = Role.valueOf(request.getRole().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid role; use END_USER, ADMIN, or SUPER_ADMIN"
            );
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setRole(newRole);
        userRepository.save(user);
        return Map.of(
                "message", "Role updated",
                "email", user.getEmail(),
                "role", newRole.name()
        );
    }

    private void requireValidInternalKey(String providedInternalKey) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal API key not configured");
        }
        if (providedInternalKey == null || !internalApiKey.equals(providedInternalKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal API key");
        }
    }

    private void addAccessTokenCookie(HttpServletResponse response, String accessToken, boolean secureRequest) {
        Cookie cookie = new Cookie(ACCESS_COOKIE_NAME, accessToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureRequest);
        cookie.setPath("/");
        cookie.setMaxAge((int) jwtUtil.getAccessTokenExpirationSeconds());
        cookie.setAttribute("SameSite", secureRequest ? "None" : "Lax");
        response.addCookie(cookie);
    }

    private void addRefreshCookie(HttpServletResponse response, String refreshToken, boolean secureRequest, int maxAgeSeconds) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureRequest);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", secureRequest ? "None" : "Lax");
        response.addCookie(cookie);
    }

    private void clearCookie(HttpServletResponse response, String name, String path, boolean secureRequest) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secureRequest);
        cookie.setPath(path);
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", secureRequest ? "None" : "Lax");
        response.addCookie(cookie);
    }

    private String readCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void issueLoginTokens(User user, boolean secureRequest, HttpServletResponse response) {
        Role role = user.getRole() != null ? user.getRole() : Role.END_USER;
        String subscription = user.getSubscription() != null ? user.getSubscription().name() : Subscription.FREE.name();
        String jti = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), role.name(), subscription, jti);
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), role.name(), subscription, jti);
        userSessionService.registerNewSession(user.getEmail(), jti, jwtUtil.getRefreshTokenExpirationSeconds());
        addAccessTokenCookie(response, accessToken, secureRequest);
        addRefreshCookie(response, refreshToken, secureRequest, (int) jwtUtil.getRefreshTokenExpirationSeconds());
    }
}
