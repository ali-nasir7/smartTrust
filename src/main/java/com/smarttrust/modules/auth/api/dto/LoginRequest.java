package com.smarttrust.modules.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank
        @Pattern(regexp = "^(\\+923\\d{9}|03\\d{9})$", message = "Invalid phone format")
        String phone,

        @NotBlank
        @Size(min = 8, max = 100)
        String password,

        // Optional FCM token for push notifications
        String fcmToken,

        // Optional device info
        String deviceInfo

) {}
