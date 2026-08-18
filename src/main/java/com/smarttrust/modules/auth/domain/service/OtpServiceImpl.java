package com.smarttrust.modules.auth.domain.service;

import com.smarttrust.common.exception.BusinessException;
import com.smarttrust.common.exception.ErrorCode;
import com.smarttrust.modules.auth.domain.entity.OtpCode;
import com.smarttrust.modules.auth.domain.enums.OtpPurpose;
import com.smarttrust.modules.auth.infrastructure.persistence.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * OTP lifecycle: generation (hashed), expiry, single-use, max attempts,
 * resend cooldown and superseding of old codes.
 *
 * All policy values come from configuration (env-overridable):
 *   smarttrust.otp.expiry-minutes, length, max-attempts, resend-cooldown-seconds
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final OtpCodeRepository otpCodeRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${smarttrust.otp.expiry-minutes:5}")
    private int expiryMinutes;

    @Value("${smarttrust.otp.length:6}")
    private int otpLength;

    @Value("${smarttrust.otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${smarttrust.otp.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    /** DEV ONLY (OTP_MOCK_ENABLED) — when true the OTP is logged to console. Never enable in production. */
    @Value("${smarttrust.otp.mock-enabled:false}")
    private boolean mockEnabled;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public OtpGenerationResult generateOtp(String phone, String email, OtpPurpose purpose) {
        // Old OTP becomes invalid the moment a new one is generated
        otpCodeRepository.supersedeActiveOtps(phone, purpose, Instant.now());

        String plainOtp = generateSecureOtp();

        OtpCode saved = otpCodeRepository.save(OtpCode.builder()
                .phone(phone)
                .email(email)
                .otpHash(passwordEncoder.encode(plainOtp)) // BCrypt — never plaintext at rest
                .purpose(purpose)
                .expiresAt(Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES))
                .attempts(0)
                .verified(false)
                .build());

        if (mockEnabled) {
            // Local development convenience only. Default false; OTP is NEVER logged otherwise.
            log.info("[DEV MOCK] OTP for phone={} purpose={} -> {} (expires in {} min)",
                    phone, purpose, plainOtp, expiryMinutes);
        } else {
            log.info("OTP generated phone={} purpose={} expiresAt={} (hash only, no plaintext logged)",
                    phone, purpose, saved.getExpiresAt());
        }

        return new OtpGenerationResult(plainOtp, saved,
                saved.getCreatedAt().plus(resendCooldownSeconds, ChronoUnit.SECONDS));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OtpCode> findActiveOtp(String phone, OtpPurpose purpose) {
        return otpCodeRepository
                .findTopByPhoneAndPurposeAndVerifiedFalseAndSupersededAtIsNullOrderByCreatedAtDesc(phone, purpose);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OtpCode> findLatestOtp(String phone, OtpPurpose purpose) {
        return otpCodeRepository.findTopByPhoneAndPurposeOrderByCreatedAtDesc(phone, purpose);
    }

    @Override
    public void verifyOtp(OtpCode otpCode, String plainOtp) {
        if (otpCode == null) {
            throw BusinessException.of(ErrorCode.AUTH_OTP_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "No active OTP found. Please request a new OTP.");
        }
        if (otpCode.isSuperseded()) {
            throw BusinessException.of(ErrorCode.AUTH_OTP_INVALID, HttpStatus.BAD_REQUEST,
                    "A newer OTP was requested. This code is no longer valid.");
        }
        if (otpCode.isExpired()) {
            throw BusinessException.of(ErrorCode.AUTH_OTP_EXPIRED, HttpStatus.BAD_REQUEST,
                    "OTP expired. Please request a new OTP.");
        }
        if (otpCode.isMaxAttemptsReached(maxAttempts)) {
            throw BusinessException.of(ErrorCode.AUTH_OTP_MAX_ATTEMPTS, HttpStatus.TOO_MANY_REQUESTS,
                    "Maximum verification attempts reached. Please request a new OTP.");
        }
        if (!passwordEncoder.matches(plainOtp, otpCode.getOtpHash())) {
            otpCode.incrementAttempts();
            otpCodeRepository.save(otpCode);
            throw BusinessException.of(ErrorCode.AUTH_OTP_INVALID, HttpStatus.BAD_REQUEST,
                    "Invalid OTP. Attempts: " + otpCode.getAttempts() + "/" + maxAttempts);
        }
    }

    @Override
    @Transactional
    public void markVerified(OtpCode otpCode) {
        otpCode.markVerified();
        otpCodeRepository.save(otpCode);
    }

    public int getResendCooldownSeconds() {
        return resendCooldownSeconds;
    }

    private String generateSecureOtp() {
        int max = (int) Math.pow(10, otpLength);          // e.g. 6 -> 1_000_000
        int min = max / 10;                                // e.g. 100_000
        int otp = min + secureRandom.nextInt(max - min);   // cryptographically random
        return String.valueOf(otp);
    }
}
