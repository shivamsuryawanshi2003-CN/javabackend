package com.jobra.authservice.service;

import com.jobra.authservice.dto.OtpRequest;
import com.jobra.authservice.entity.User;
import com.jobra.authservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class OtpService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int OTP_TTL_MINUTES = 10;

    private final SecureRandom secureRandom = new SecureRandom();
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final OtpIpRateLimiter otpIpRateLimiter;

    public OtpService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            OtpIpRateLimiter otpIpRateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.otpIpRateLimiter = otpIpRateLimiter;
    }

    /**
     * Sends a new OTP email. Enforces per-IP hourly rate ({@link OtpIpRateLimiter}).
     */
    public void generateForUser(User user, String clientIp) {
        otpIpRateLimiter.checkAndRecord(clientIp);
        generateOtpAndEmail(user);
    }

    /** Sends OTP without IP rate limiting (e.g. tests). Prefer {@link #generateForUser(User, String)}. */
    public void generateForUserWithoutRateLimit(User user) {
        generateOtpAndEmail(user);
    }

    private void generateOtpAndEmail(User user) {
        String otp = String.valueOf(100000 + secureRandom.nextInt(900000));
        user.setOtp(passwordEncoder.encode(otp));
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_TTL_MINUTES));
        user.setOtpAttempts(0);
        userRepository.save(user);
        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    public User verify(OtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User already verified");
        }
        if (user.getOtp() == null || user.getOtpExpiry() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP not generated");
        }
        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired");
        }
        if (user.getOtpAttempts() >= MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum OTP attempts exceeded");
        }
        if (!passwordEncoder.matches(request.getOtp(), user.getOtp())) {
            user.setOtpAttempts(user.getOtpAttempts() + 1);
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP");
        }

        user.setActive(true);
        user.setOtp(null);
        user.setOtpExpiry(null);
        user.setOtpAttempts(0);
        return userRepository.save(user);
    }
}
