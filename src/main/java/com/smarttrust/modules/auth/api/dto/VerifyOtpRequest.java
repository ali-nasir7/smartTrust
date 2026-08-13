package com.smarttrust.modules.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(

        @NotBlank
        @Pattern(regexp = "^(\\+923\\d{9}|03\\d{9})$", message = "Invalid phone format")
        String phone,

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
        String otp
) {}
