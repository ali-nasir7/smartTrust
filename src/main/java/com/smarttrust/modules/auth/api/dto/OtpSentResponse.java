package com.smarttrust.modules.auth.api.dto;

import java.time.Instant;

/**
 * Response for register/init, resend-otp and forgot-password/init.
 * IMPORTANT: it never contains the OTP itself — the code is delivered by email only.
 */
public record OtpSentResponse(
        Long userId,
        String phone,
        String maskedEmail,        // e.g. ali***@gmail.com — do not expose full PII
        String message,
        Instant otpExpiresAt,
        Instant resendAvailableAt  // earliest time a resend request will be accepted
) {}
