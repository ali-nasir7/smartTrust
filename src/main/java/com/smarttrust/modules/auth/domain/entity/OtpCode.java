package com.smarttrust.modules.auth.domain.entity;

import com.smarttrust.modules.auth.domain.enums.OtpPurpose;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "auth_otp_codes", indexes = {
        @Index(name = "idx_otp_phone_purpose_created", columnList = "phone, purpose, created_at"),
        @Index(name = "idx_otp_email_purpose_created", columnList = "email, purpose, created_at"),
        @Index(name = "idx_otp_expires", columnList = "expires_at")
})
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    /** Delivery target for this OTP (email). Nullable for legacy phone-only flows. */
    @Column(name = "email", length = 190)
    private String email;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash; // BCrypt hash — never plaintext

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "verified", nullable = false)
    private boolean verified = false;

    /** Set when a newer OTP was generated — old OTP becomes invalid immediately. */
    @Column(name = "superseded_at")
    private Instant supersededAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isSuperseded() {
        return supersededAt != null;
    }

    public boolean isMaxAttemptsReached(int maxAttempts) {
        return attempts >= maxAttempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public void markVerified() {
        this.verified = true;
    }

    public void supersede() {
        this.supersededAt = Instant.now();
    }
}
