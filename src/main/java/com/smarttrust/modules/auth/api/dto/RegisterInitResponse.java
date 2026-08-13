package com.smarttrust.modules.auth.api.dto;

import java.time.Instant;

public record RegisterInitResponse(
        Long userId,
        String phone,
        String role,
        String message,
        Instant otpExpiresAt,
        // For DEV only, returned when mock enabled. Null in prod.
        String mockOtpForTesting
) {}
