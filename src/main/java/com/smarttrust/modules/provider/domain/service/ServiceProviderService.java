package com.smarttrust.modules.provider.domain.service;

import com.smarttrust.common.exception.BusinessException;
import com.smarttrust.common.exception.ErrorCode;
import com.smarttrust.common.storage.FileStorageService;
import com.smarttrust.modules.provider.api.dto.*;
import com.smarttrust.modules.provider.domain.entity.ProviderDocument;
import com.smarttrust.modules.provider.domain.entity.ServiceCategory;
import com.smarttrust.modules.provider.domain.entity.ServiceProviderProfile;
import com.smarttrust.modules.provider.domain.enums.ProviderDocumentType;
import com.smarttrust.modules.provider.domain.enums.ProviderVerificationStatus;
import com.smarttrust.modules.provider.infrastructure.persistence.ProviderDocumentRepository;
import com.smarttrust.modules.provider.infrastructure.persistence.ServiceCategoryRepository;
import com.smarttrust.modules.provider.infrastructure.persistence.ServiceProviderProfileRepository;
import com.smarttrust.modules.user.domain.entity.User;
import com.smarttrust.modules.user.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceProviderService {

    private final ServiceProviderProfileRepository profileRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final ProviderDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    // ---- PROVIDER: detailed profile form ----

    @Transactional
    public ProviderProfileResponse upsertMyProfile(Long userId, ProviderProfileRequest request) {
        ServiceCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.CATEGORY_NOT_FOUND, HttpStatus.BAD_REQUEST,
                        "Category not found: " + request.categoryId()));
        if (!category.isActive()) {
            throw BusinessException.of(ErrorCode.CATEGORY_NOT_FOUND, HttpStatus.BAD_REQUEST,
                    "Category is not active: " + category.getName());
        }

        ServiceProviderProfile profile = profileRepository.findByUserId(userId)
                .orElseGet(() -> ServiceProviderProfile.builder()
                        .userId(userId)
                        .verificationStatus(ProviderVerificationStatus.NOT_SUBMITTED)
                        .build());

        profile.setFullName(request.fullName().trim());
        profile.setCategoryId(category.getId());
        profile.setExperienceYears(request.experienceYears());
        profile.setSkills(joinSkills(request.skills()));
        profile.setBio(request.bio());
        profile.setAddress(request.address().trim());
        profile.setCity(request.city().trim());

        // NOTE: editing the profile never resets an APPROVED/REJECTED state by itself;
        // a new document submission always triggers re-review.
        ServiceProviderProfile saved = profileRepository.save(profile);
        log.info("Provider profile saved userId={} profileId={} status={}",
                userId, saved.getId(), saved.getVerificationStatus());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProviderProfileResponse getMyProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PROVIDER_PROFILE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Provider profile not completed yet"));
    }

    // ---- PROVIDER: CNIC + selfie upload -> PENDING_REVIEW ----

    @Transactional
    public ProviderProfileResponse uploadDocuments(Long userId,
                                                   MultipartFile cnicFront,
                                                   MultipartFile cnicBack,
                                                   MultipartFile selfie) {
        ServiceProviderProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PROVIDER_PROFILE_NOT_FOUND, HttpStatus.BAD_REQUEST,
                        "Complete your provider profile before uploading documents"));

        storeOne(userId, ProviderDocumentType.CNIC_FRONT, cnicFront);
        storeOne(userId, ProviderDocumentType.CNIC_BACK, cnicBack);
        storeOne(userId, ProviderDocumentType.SELFIE, selfie);

        // Fresh submission (or resubmission after REJECTED, or re-verification after APPROVED)
        profile.submitForReview();
        ServiceProviderProfile saved = profileRepository.save(profile);
        log.info("Provider documents submitted for review userId={} profileId={}", userId, saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProviderDocumentResponse> getMyDocuments(Long userId) {
        return documentRepository.findByUserIdOrderByDocTypeAsc(userId).stream()
                .map(ServiceProviderService::toDocResponse)
                .toList();
    }

    /** Streams a stored document — allowed for the owner OR an admin (enforced by callers). */
    @Transactional(readOnly = true)
    public ProviderDocument loadDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.DOCUMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Document not found"));
    }

    @Transactional(readOnly = true)
    public java.nio.file.Path resolveDocumentPath(ProviderDocument doc) {
        return fileStorageService.resolve(doc.getFilePath());
    }

    // ---- ADMIN: review queue ----

    @Transactional(readOnly = true)
    public PageResponse<AdminProviderSummary> listProviders(ProviderVerificationStatus status, Pageable pageable) {
        Page<ServiceProviderProfile> page = status != null
                ? profileRepository.findByVerificationStatus(status, pageable)
                : profileRepository.findAllByOrderByUpdatedAtDesc(pageable);
        return PageResponse.of(page.map(this::toAdminSummary));
    }

    @Transactional(readOnly = true)
    public AdminProviderSummary getProvider(Long profileId) {
        return toAdminSummary(profileRepository.findById(profileId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PROVIDER_PROFILE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Provider not found")));
    }

    @Transactional
    public AdminProviderSummary approve(Long profileId, Long adminUserId) {
        ServiceProviderProfile profile = requirePendingReview(profileId);
        profile.approve(adminUserId);
        ServiceProviderProfile saved = profileRepository.save(profile);
        log.info("Provider APPROVED profileId={} userId={} admin={}", profileId, saved.getUserId(), adminUserId);
        return toAdminSummary(saved);
    }

    @Transactional
    public AdminProviderSummary reject(Long profileId, Long adminUserId, String reason) {
        ServiceProviderProfile profile = requirePendingReview(profileId);
        profile.reject(adminUserId, reason);
        ServiceProviderProfile saved = profileRepository.save(profile);
        log.info("Provider REJECTED profileId={} userId={} admin={}", profileId, saved.getUserId(), adminUserId);
        return toAdminSummary(saved);
    }

    // ---- Categories ----

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getDescription()))
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(String name, String description) {
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw BusinessException.of(ErrorCode.CATEGORY_ALREADY_EXISTS, HttpStatus.CONFLICT,
                    "Category already exists: " + name);
        }
        ServiceCategory saved = categoryRepository.save(ServiceCategory.builder()
                .name(name.trim())
                .description(description)
                .active(true)
                .build());
        return new CategoryResponse(saved.getId(), saved.getName(), saved.getDescription());
    }

    // ---- helpers ----

    /**
     * Upserts the document row for (userId, docType): stores the new file first,
     * then updates the existing row (avoids delete+insert unique-key races within one flush).
     */
    private void storeOne(Long userId, ProviderDocumentType type, MultipartFile file) {
        ProviderDocument doc = documentRepository.findByUserIdAndDocType(userId, type)
                .orElseGet(() -> ProviderDocument.builder().userId(userId).docType(type).build());
        String oldPath = doc.getId() != null ? doc.getFilePath() : null;

        // Store new file first; if this fails nothing is modified in DB
        String newPath = fileStorageService.storeDocument(userId, type.name().toLowerCase(Locale.ROOT), file);

        doc.setFilePath(newPath);
        doc.setOriginalName(file.getOriginalFilename());
        doc.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        doc.setFileSizeBytes(file.getSize());
        documentRepository.save(doc);

        if (oldPath != null) {
            fileStorageService.deleteQuietly(oldPath); // best-effort cleanup of replaced file
        }
    }

    private ServiceProviderProfile requirePendingReview(Long profileId) {
        ServiceProviderProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PROVIDER_PROFILE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Provider not found"));
        if (profile.getVerificationStatus() != ProviderVerificationStatus.PENDING_REVIEW) {
            throw BusinessException.of(ErrorCode.PROVIDER_INVALID_STATUS_TRANSITION, HttpStatus.CONFLICT,
                    "Provider is not awaiting review (current status: "
                            + profile.getVerificationStatus() + ")");
        }
        return profile;
    }

    private static String joinSkills(List<String> skills) {
        if (skills == null || skills.isEmpty()) return null;
        return String.join(",", skills.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    private static List<String> splitSkills(String skills) {
        if (skills == null || skills.isBlank()) return List.of();
        return Arrays.stream(skills.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private ProviderProfileResponse toResponse(ServiceProviderProfile p) {
        String categoryName = categoryRepository.findById(p.getCategoryId())
                .map(ServiceCategory::getName).orElse(null);
        return new ProviderProfileResponse(
                p.getId(), p.getUserId(), p.getFullName(), p.getCategoryId(), categoryName,
                p.getExperienceYears(), splitSkills(p.getSkills()), p.getBio(), p.getAddress(), p.getCity(),
                p.getVerificationStatus().name(), p.getRejectionReason(), p.getVerifiedAt(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private AdminProviderSummary toAdminSummary(ServiceProviderProfile p) {
        User user = userRepository.findById(p.getUserId()).orElse(null);
        String categoryName = categoryRepository.findById(p.getCategoryId())
                .map(ServiceCategory::getName).orElse(null);
        List<ProviderDocumentResponse> docs = documentRepository.findByUserIdOrderByDocTypeAsc(p.getUserId())
                .stream().map(ServiceProviderService::toDocResponse).toList();
        return new AdminProviderSummary(
                p.getId(), p.getUserId(), p.getFullName(),
                user != null ? user.getPhone() : null,
                user != null ? user.getEmail() : null,
                categoryName, p.getExperienceYears(), p.getCity(),
                p.getVerificationStatus().name(), p.getRejectionReason(),
                p.getUpdatedAt(), p.getVerifiedAt(), docs);
    }

    static ProviderDocumentResponse toDocResponse(ProviderDocument d) {
        return new ProviderDocumentResponse(d.getId(), d.getDocType().name(), d.getOriginalName(),
                d.getContentType(), d.getFileSizeBytes(), d.getCreatedAt());
    }
}
