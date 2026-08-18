package com.smarttrust.modules.auth.api.dto;

import com.smarttrust.modules.user.domain.enums.UserRole;
import jakarta.validation.constraints.*;

public record RegisterInitRequest(

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^(\\+923\\d{9}|03\\d{9})$",
                message = "Phone must be valid PK format: 03XXXXXXXXX or +923XXXXXXXXX")
        String phone,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid", regexp = "^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$")
        @Size(max = 190, message = "Email max 190 chars")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be 8-100 chars")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "Password must contain at least 1 uppercase, 1 lowercase and 1 digit")
        String password,

        /**
         * Optional (v2): the user chooses CUSTOMER or SERVICE_PROVIDER AFTER email
         * verification via /api/v1/auth/select-role. Kept for backward compatibility —
         * if an old client sends it, the role is set immediately.
         */
        UserRole role,

        /** Optional convenience — the profile forms (customer/provider) are authoritative. */
        @Size(min = 2, max = 120, message = "Full name 2-120 chars")
        String fullName

) {}
