package com.smarttrust.common.security;

import com.smarttrust.common.exception.BusinessException;
import com.smarttrust.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Helper to read the authenticated user from the JWT-derived security context. */
public final class SecurityUtils {

    private SecurityUtils() {}

    /** User id from the JWT subject. Throws UNAUTHORIZED when no valid JWT is present. */
    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getId();
    }
}
