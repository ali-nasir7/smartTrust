package com.smarttrust.modules.customer.api;

import com.smarttrust.common.security.SecurityUtils;
import com.smarttrust.modules.customer.api.dto.CustomerProfileRequest;
import com.smarttrust.modules.customer.api.dto.CustomerProfileResponse;
import com.smarttrust.modules.customer.domain.service.CustomerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Customer Module", description = "Customer short-form profile (after role selection)")
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;

    @Operation(summary = "Save/update my customer profile (short form: name, address, city)")
    @PostMapping("/profile")
    public ResponseEntity<CustomerProfileResponse> saveProfile(@Valid @RequestBody CustomerProfileRequest request) {
        Long userId = SecurityUtils.currentUserId();
        log.info("POST /customers/profile userId={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(customerProfileService.upsertMyProfile(userId, request));
    }

    @Operation(summary = "Get my customer profile")
    @GetMapping("/profile")
    public ResponseEntity<CustomerProfileResponse> getProfile() {
        return ResponseEntity.ok(customerProfileService.getMyProfile(SecurityUtils.currentUserId()));
    }
}
