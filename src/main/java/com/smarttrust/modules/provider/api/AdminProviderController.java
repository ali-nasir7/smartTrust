package com.smarttrust.modules.provider.api;

import com.smarttrust.common.security.SecurityUtils;
import com.smarttrust.modules.provider.api.dto.AdminProviderSummary;
import com.smarttrust.modules.provider.api.dto.CategoryResponse;
import com.smarttrust.modules.provider.api.dto.PageResponse;
import com.smarttrust.modules.provider.api.dto.RejectRequest;
import com.smarttrust.modules.provider.domain.entity.ProviderDocument;
import com.smarttrust.modules.provider.domain.enums.ProviderVerificationStatus;
import com.smarttrust.modules.provider.domain.service.ServiceProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Module", description = "Provider verification review: list, inspect, approve, reject")
public class AdminProviderController {

    private final ServiceProviderService providerService;

    // ---- Provider review queue ----

    @Operation(summary = "List providers (filter: ?status=PENDING_REVIEW&page=0&size=20)")
    @GetMapping("/providers")
    public ResponseEntity<PageResponse<AdminProviderSummary>> listProviders(
            @RequestParam(required = false) ProviderVerificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(providerService.listProviders(status, pageable));
    }

    @Operation(summary = "Get one provider submission (profile + document metadata)")
    @GetMapping("/providers/{id}")
    public ResponseEntity<AdminProviderSummary> getProvider(@PathVariable Long id) {
        return ResponseEntity.ok(providerService.getProvider(id));
    }

    @Operation(summary = "Approve a provider under review -> provider becomes VERIFIED (APPROVED)")
    @PostMapping("/providers/{id}/approve")
    public ResponseEntity<AdminProviderSummary> approve(@PathVariable Long id) {
        Long adminId = SecurityUtils.currentUserId();
        log.info("Admin {} approving provider profile {}", adminId, id);
        return ResponseEntity.ok(providerService.approve(id, adminId));
    }

    @Operation(summary = "Reject a provider under review (reason stored, resubmission allowed)")
    @PostMapping("/providers/{id}/reject")
    public ResponseEntity<AdminProviderSummary> reject(@PathVariable Long id,
                                                       @Valid @RequestBody RejectRequest request) {
        Long adminId = SecurityUtils.currentUserId();
        log.info("Admin {} rejecting provider profile {}", adminId, id);
        return ResponseEntity.ok(providerService.reject(id, adminId, request.reason()));
    }

    @Operation(summary = "Download/view a verification document (CNIC/selfie) — admin only")
    @GetMapping("/providers/documents/{documentId}/file")
    public ResponseEntity<Resource> documentFile(@PathVariable Long documentId) {
        ProviderDocument doc = providerService.loadDocument(documentId);
        Path path = providerService.resolveDocumentPath(doc);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        doc.getContentType() != null ? doc.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + (doc.getId()) + "_" + doc.getDocType().name() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new FileSystemResource(path));
    }

    // ---- Category management ----

    @Operation(summary = "Create a service category")
    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(
            @RequestBody CategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(providerService.createCategory(request.name(), request.description()));
    }

    public record CategoryCreateRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @Size(max = 255) String description) {}
}
