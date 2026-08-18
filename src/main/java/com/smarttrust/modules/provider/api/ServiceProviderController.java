package com.smarttrust.modules.provider.api;

import com.smarttrust.common.security.SecurityUtils;
import com.smarttrust.modules.provider.api.dto.ProviderDocumentResponse;
import com.smarttrust.modules.provider.api.dto.ProviderProfileRequest;
import com.smarttrust.modules.provider.api.dto.ProviderProfileResponse;
import com.smarttrust.modules.provider.domain.service.ServiceProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
@Tag(name = "Provider Module", description = "Detailed provider profile + CNIC/selfie verification documents")
public class ServiceProviderController {

    private final ServiceProviderService providerService;

    @Operation(summary = "Save/update my provider profile (detailed form)",
            description = "fullName, category, experienceYears, skills, bio, address, city. "
                    + "Verification status starts as NOT_SUBMITTED and changes to PENDING_REVIEW after document upload.")
    @PostMapping("/profile")
    public ResponseEntity<ProviderProfileResponse> saveProfile(@Valid @RequestBody ProviderProfileRequest request) {
        Long userId = SecurityUtils.currentUserId();
        log.info("POST /providers/profile userId={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(providerService.upsertMyProfile(userId, request));
    }

    @Operation(summary = "Get my provider profile (includes verification status + rejection reason)")
    @GetMapping("/profile")
    public ResponseEntity<ProviderProfileResponse> getProfile() {
        return ResponseEntity.ok(providerService.getMyProfile(SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Upload verification documents (multipart): CNIC front + CNIC back + selfie",
            description = "JPG/PNG/WEBP, max 5MB each. Sets status to PENDING_REVIEW. "
                    + "If previously REJECTED, this is the resubmission path. Re-uploading after APPROVED re-opens review.")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProviderProfileResponse> uploadDocuments(
            @RequestPart("cnicFront") MultipartFile cnicFront,
            @RequestPart("cnicBack") MultipartFile cnicBack,
            @RequestPart("selfie") MultipartFile selfie) {
        Long userId = SecurityUtils.currentUserId();
        log.info("POST /providers/documents userId={} sizes={},{},{}",
                userId, cnicFront.getSize(), cnicBack.getSize(), selfie.getSize());
        return ResponseEntity.accepted()
                .body(providerService.uploadDocuments(userId, cnicFront, cnicBack, selfie));
    }

    @Operation(summary = "List my uploaded documents (metadata only)")
    @GetMapping("/documents")
    public ResponseEntity<List<ProviderDocumentResponse>> myDocuments() {
        return ResponseEntity.ok(providerService.getMyDocuments(SecurityUtils.currentUserId()));
    }
}
