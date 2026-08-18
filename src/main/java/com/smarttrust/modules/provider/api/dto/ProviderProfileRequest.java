package com.smarttrust.modules.provider.api.dto;

import jakarta.validation.constraints.*;

import java.util.List;

/** Detailed provider form (work experience, skills, category, etc.). */
public record ProviderProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Full name 2-120 chars")
        String fullName,

        @NotNull(message = "Category is required (see GET /api/v1/categories)")
        @Positive(message = "Category id must be a positive number")
        Long categoryId,

        @NotNull(message = "Experience (years) is required")
        @Min(value = 0, message = "Experience cannot be negative")
        @Max(value = 50, message = "Experience max 50 years")
        Integer experienceYears,

        List<String> skills,   // e.g. ["Wiring","Fan install"] — optional

        @Size(max = 500, message = "Bio max 500 chars")
        String bio,            // optional

        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address max 255 chars")
        String address,

        @NotBlank(message = "City is required")
        @Size(max = 80, message = "City max 80 chars")
        String city
) {}
