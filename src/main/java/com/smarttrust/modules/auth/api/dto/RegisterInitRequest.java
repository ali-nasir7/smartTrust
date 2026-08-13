package com.smarttrust.modules.auth.api.dto;

import com.smarttrust.modules.user.domain.enums.UserRole;
import jakarta.validation.constraints.*;

public record RegisterInitRequest(

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^(\\+923\\d{9}|03\\d{9})$",
                message = "Phone must be valid PK format: 03XXXXXXXXX or +923XXXXXXXXX")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be 8-100 chars")
        // Optional strong password pattern: at least 1 upper, 1 lower, 1 digit
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "Password must contain at least 1 uppercase, 1 lowercase and 1 digit")
        String password,

        @NotNull(message = "Role is required")
        UserRole role,

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Full name 2-120 chars")
        String fullName

) {}
