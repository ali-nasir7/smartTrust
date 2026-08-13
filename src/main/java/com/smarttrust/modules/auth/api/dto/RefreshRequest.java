package com.smarttrust.modules.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Refresh token required")
        String refreshToken
) {}
