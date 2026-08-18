package com.smarttrust.modules.customer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Short customer form (phone comes from the account, not the form). */
public record CustomerProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Full name 2-120 chars")
        String fullName,

        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address max 255 chars")
        String address,

        @NotBlank(message = "City is required")
        @Size(max = 80, message = "City max 80 chars")
        String city
) {}
