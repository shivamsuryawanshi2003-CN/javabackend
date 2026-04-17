package com.jobra.authservice.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    private String provider;

    /** Base32 TOTP secret; set when MFA is enabled for the account. */
    @Column(length = 64)
    private String mfaSecret;

    private boolean mfaEnabled = false;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private Subscription subscription=Subscription.FREE;

    private boolean verified = false;
    private String otp;
    private LocalDateTime otpExpiry;
    private Integer otpAttempts = 0;
    private String passwordResetOtp;
    private LocalDateTime passwordResetOtpExpiry;
    private Integer passwordResetOtpAttempts = 0;

    public boolean isActive() {
        return verified;
    }

    public void setActive(boolean active) {
        this.verified = active;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public LocalDateTime getOtpExpiry() {
        return otpExpiry;
    }

    public void setOtpExpiry(LocalDateTime otpExpiry) {
        this.otpExpiry = otpExpiry;
    }

    public int getOtpAttempts() {
        return otpAttempts == null ? 0 : otpAttempts;
    }

    public void setOtpAttempts(int otpAttempts) {
        this.otpAttempts = otpAttempts;
    }

    public String getPasswordResetOtp() {
        return passwordResetOtp;
    }

    public void setPasswordResetOtp(String passwordResetOtp) {
        this.passwordResetOtp = passwordResetOtp;
    }

    public LocalDateTime getPasswordResetOtpExpiry() {
        return passwordResetOtpExpiry;
    }

    public void setPasswordResetOtpExpiry(LocalDateTime passwordResetOtpExpiry) {
        this.passwordResetOtpExpiry = passwordResetOtpExpiry;
    }

    public int getPasswordResetOtpAttempts() {
        return passwordResetOtpAttempts == null ? 0 : passwordResetOtpAttempts;
    }

    public void setPasswordResetOtpAttempts(int passwordResetOtpAttempts) {
        this.passwordResetOtpAttempts = passwordResetOtpAttempts;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public void setMfaSecret(String mfaSecret) {
        this.mfaSecret = mfaSecret;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }
}
