package com.smarttrust.modules.provider.api.dto;

import java.time.Instant;
import java.util.List;

public record ProviderProfileResponse(
        Long id,
        Long userId,
        String fullName,
        Long categoryId,
        String categoryName,
        int experienceYears,
        List<String> skills,
        String bio,
        String address,
        String city,
        String verificationStatus,   // NOT_SUBMITTED | PENDING_REVIEW | APPROVED | REJECTED
        String rejectionReason,      // set when REJECTED
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt
) {}
