package com.smarttrust.modules.customer.api.dto;

import java.time.Instant;

public record CustomerProfileResponse(
        Long id,
        Long userId,
        String fullName,
        String address,
        String city,
        Instant createdAt,
        Instant updatedAt
) {}
