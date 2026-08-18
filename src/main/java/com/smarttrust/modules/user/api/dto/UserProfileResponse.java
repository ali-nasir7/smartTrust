package com.smarttrust.modules.user.api.dto;

import java.time.Instant;

public record UserProfileResponse(
        Long id,
        String phone,
        String email,
        String fullName,
        String role,      // null until the user selects CUSTOMER or SERVICE_PROVIDER
        String status,
        boolean phoneVerified,
        boolean emailVerified,
        Instant createdAt
) {}
