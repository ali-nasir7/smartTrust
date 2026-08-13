package com.smarttrust.modules.auth.api.dto;

import java.time.Instant;

public record AuthResponse(
        Long userId,
        String phone,
        String role,
        String status,
        String accessToken,
        String refreshToken,
        long expiresIn, // seconds
        Instant accessExpiresAt,
        Instant refreshExpiresAt
) {}
