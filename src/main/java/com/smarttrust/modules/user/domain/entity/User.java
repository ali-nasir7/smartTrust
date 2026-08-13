package com.smarttrust.modules.user.domain.entity;

import com.smarttrust.modules.user.domain.enums.UserRole;
import com.smarttrust.modules.user.domain.enums.UserStatus;
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
@Table(name = "users", indexes = {
        @Index(name = "idx_users_role_status", columnList = "role, status")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone", nullable = false, length = 20, unique = true)
    private String phone;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Column(name = "email", length = 190)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // Business methods
    public void markPhoneVerified() {
        this.phoneVerified = true;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.phoneVerified = true;
    }

    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public boolean isSuspendedOrBanned() {
        return this.status == UserStatus.SUSPENDED || this.status == UserStatus.BANNED;
    }
}
