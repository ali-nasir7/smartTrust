package com.smarttrust.modules.user.api;

import com.smarttrust.common.exception.BusinessException;
import com.smarttrust.common.exception.ErrorCode;
import com.smarttrust.common.security.SecurityUtils;
import com.smarttrust.modules.user.api.dto.UserProfileResponse;
import com.smarttrust.modules.user.domain.entity.User;
import com.smarttrust.modules.user.infrastructure.persistence.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Module", description = "Current authenticated user info")
public class UserController {

    private final UserRepository userRepository;

    @Operation(summary = "Get current authenticated user",
            description = "Returns id, phone, email, fullName, role (null until selected), status and flags. "
                    + "Used by the client to decide whether to show the role-selection screen.")
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me() {
        User user = userRepository.findById(SecurityUtils.currentUserId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "User not found"));

        return ResponseEntity.ok(new UserProfileResponse(
                user.getId(),
                user.getPhone(),
                user.getEmail(),
                user.getFullName(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getStatus().name(),
                user.isPhoneVerified(),
                user.isEmailVerified(),
                user.getCreatedAt()
        ));
    }
}
