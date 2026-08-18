package com.smarttrust.modules.auth.api.dto;

import com.smarttrust.modules.user.domain.enums.UserRole;
import jakarta.validation.constraints.NotNull;

public record SelectRoleRequest(

        @NotNull(message = "Role is required: CUSTOMER or SERVICE_PROVIDER")
        UserRole role
) {}
