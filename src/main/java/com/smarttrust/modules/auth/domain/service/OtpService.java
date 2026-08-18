package com.smarttrust.modules.auth.domain.service;

import com.smarttrust.modules.auth.domain.entity.OtpCode;
import com.smarttrust.modules.auth.domain.enums.OtpPurpose;

import java.time.Instant;
import java.util.Optional;

public interface OtpService {

    /** Plain OTP + persisted (hashed) entity. Supersedes all previous active OTPs for phone+purpose. */
    OtpGenerationResult generateOtp(String phone, String email, OtpPurpose purpose);

    /** The single active OTP for phone+purpose (not verified, not superseded, newest first). */
    Optional<OtpCode> findActiveOtp(String phone, OtpPurpose purpose);

    /** Latest OTP row of any state — used for resend-cooldown enforcement. */
    Optional<OtpCode> findLatestOtp(String phone, OtpPurpose purpose);

    /**
     * Verifies the plain OTP against the hash; increments attempts on mismatch and throws.
     * Checks expiry, superseded state and max attempts.
     */
    void verifyOtp(OtpCode otpCode, String plainOtp);

    /** Marks the OTP as used — single-use enforcement. */
    void markVerified(OtpCode otpCode);

    /** Resend cooldown in seconds (from config, env-overridable). */
    int getResendCooldownSeconds();

    record OtpGenerationResult(String plainOtp, OtpCode entity, Instant resendAvailableAt) {}
}
