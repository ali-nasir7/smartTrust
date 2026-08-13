package com.smarttrust.modules.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ForgotPasswordResetRequest(

        @NotBlank
        @Pattern(regexp = "^(\\+923\\d{9}|03\\d{9})$")
        String phone,

        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
        String otp,

        @NotBlank
        @Size(min = 8, max = 100)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "Password must contain at least 1 uppercase, 1 lowercase and 1 digit")
        String newPassword
) {}
