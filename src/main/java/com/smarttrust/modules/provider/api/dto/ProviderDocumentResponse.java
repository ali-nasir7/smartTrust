package com.smarttrust.modules.provider.api.dto;

import java.time.Instant;

public record ProviderDocumentResponse(
        Long id,
        String docType,
        String originalName,
        String contentType,
        long fileSizeBytes,
        Instant uploadedAt
) {}
