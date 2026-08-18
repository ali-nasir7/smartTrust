package com.smarttrust.modules.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResendOtpRequest(

        @NotBlank
        @Pattern(regexp = "^(\\+923\\d{9}|03\\d{9})$", message = "Invalid phone format")
        String phone
) {}
