package com.jobra.authservice.controller;

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
import dev.samstevens.totp.exceptions.QrGenerationException;
import com.jobra.authservice.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.register(request, httpRequest));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody OtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse response) {
        return ResponseEntity.ok(authService.login(request, httpRequest.isSecure(), response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(authService.refresh(request, response));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response, request.isSecure());
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestParam String email, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.resendOtp(email, httpRequest));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                  HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.requestPasswordReset(request, httpRequest));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return ResponseEntity.ok(authService.confirmPasswordReset(request));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        return ResponseEntity.ok(authService.currentUser(authentication));
    }

    /** Session probe for SPAs — 200 with {@code authenticated: false} when logged out (avoids noisy 401 in DevTools). */
    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(Authentication authentication) {
        return ResponseEntity.ok(authService.sessionUser(authentication));
    }

    @GetMapping("/users/by-email")
    public Map<String, Object> getUserByEmail(@RequestParam String email) {
        return authService.getUserByEmail(email);
    }

    @PostMapping("/internal/subscription")
    public ResponseEntity<?> updateSubscription(@RequestHeader(name = "X-Internal-Key", required = false) String internalKey,
                                                @Valid @RequestBody SubscriptionUpdateRequest request) {
        return ResponseEntity.ok(authService.updateSubscription(internalKey, request));
    }

    @PostMapping("/internal/role")
    public ResponseEntity<?> updateRoleInternal(@RequestHeader(name = "X-Internal-Key", required = false) String internalKey,
                                                @Valid @RequestBody InternalRoleUpdateRequest request) {
        return ResponseEntity.ok(authService.updateRoleInternal(internalKey, request));
    }

    @PostMapping("/mfa/enroll/start")
    public ResponseEntity<?> mfaEnrollStart(@Valid @RequestBody MfaEnrollStartRequest request) throws QrGenerationException {
        return ResponseEntity.ok(authService.mfaEnrollStart(request));
    }

    @PostMapping("/mfa/enroll/confirm")
    public ResponseEntity<?> mfaEnrollConfirm(@Valid @RequestBody MfaEnrollConfirmRequest request) {
        return ResponseEntity.ok(authService.mfaEnrollConfirm(request));
    }

    @PostMapping("/oauth/mfa/verify")
    public ResponseEntity<?> verifyOAuthMfa(@Valid @RequestBody OAuthMfaVerifyRequest request,
                                            HttpServletRequest httpRequest,
                                            HttpServletResponse response) {
        return ResponseEntity.ok(authService.verifyOAuthMfa(request, httpRequest.isSecure(), response));
    }
}
