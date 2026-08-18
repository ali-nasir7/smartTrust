package com.smarttrust.modules.provider.api.dto;

import java.time.Instant;
import java.util.List;

/** Admin-facing provider review item (profile + account info + document metadata). */
public record AdminProviderSummary(
        Long id,
        Long userId,
        String fullName,
        String phone,
        String email,
        String categoryName,
        int experienceYears,
        String city,
        String verificationStatus,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt,
        List<ProviderDocumentResponse> documents
) {}
