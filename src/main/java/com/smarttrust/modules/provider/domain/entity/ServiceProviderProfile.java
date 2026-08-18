package com.smarttrust.modules.provider.domain.entity;

import com.smarttrust.modules.provider.domain.enums.ProviderVerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "service_provider_profiles", indexes = {
        @Index(name = "idx_provider_status", columnList = "verification_status")
})
public class ServiceProviderProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "experience_years", nullable = false)
    private int experienceYears;

    /** Comma-separated skills, e.g. "Wiring,Fan install,DB boards". */
    @Column(name = "skills", columnDefinition = "TEXT")
    private String skills;

    @Column(name = "bio", length = 500)
    private String bio;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private ProviderVerificationStatus verificationStatus = ProviderVerificationStatus.NOT_SUBMITTED;

    /** Set when an admin rejects; cleared on resubmission. */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** Admin user id that approved/rejected. */
    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // ---- Domain behaviour ----

    public void submitForReview() {
        this.verificationStatus = ProviderVerificationStatus.PENDING_REVIEW;
        this.rejectionReason = null;
        this.verifiedAt = null;
        this.reviewedByUserId = null;
    }

    public void approve(Long adminUserId) {
        this.verificationStatus = ProviderVerificationStatus.APPROVED;
        this.rejectionReason = null;
        this.verifiedAt = Instant.now();
        this.reviewedByUserId = adminUserId;
    }

    public void reject(Long adminUserId, String reason) {
        this.verificationStatus = ProviderVerificationStatus.REJECTED;
        this.rejectionReason = reason;
        this.verifiedAt = null;
        this.reviewedByUserId = adminUserId;
    }
}
