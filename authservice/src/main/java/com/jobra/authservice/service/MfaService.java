package com.jobra.authservice.service;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
public class MfaService {

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
    private final ZxingPngQrGenerator qrGenerator = new ZxingPngQrGenerator();

    @Value("${spring.application.name:authservice}")
    private String issuer;

    public String generateSecret() {
        return secretGenerator.generate();
    }

    private QrData qrData(String email, String secret) {
        return new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(issuer)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
    }

    public String otpAuthUri(String email, String secret) {
        return qrData(email, secret).getUri();
    }

    public byte[] qrPng(String email, String secret) throws QrGenerationException {
        return qrGenerator.generate(qrData(email, secret));
    }

    public boolean verifyTotp(String base32Secret, String code) {
        if (base32Secret == null || code == null || code.isBlank()) {
            return false;
        }
        return codeVerifier.isValidCode(base32Secret, code.trim());
    }

    public String qrImageDataUrl(byte[] pngBytes) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
    }
}
