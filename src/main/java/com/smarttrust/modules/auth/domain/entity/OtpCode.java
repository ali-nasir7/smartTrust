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
        @Index(name = "idx_otp_expires", columnList = "expires_at")
})
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash; // BCrypt hash

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "verified", nullable = false)
    private boolean verified = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
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
}
