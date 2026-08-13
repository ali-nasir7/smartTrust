package com.smarttrust.modules.auth.domain.entity;

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
@Table(name = "auth_login_attempts", indexes = {
        @Index(name = "idx_login_phone_time", columnList = "phone, attempted_at"),
        @Index(name = "idx_login_ip_time", columnList = "ip_address, attempted_at")
})
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @PrePersist
    public void prePersist() {
        if (attemptedAt == null) attemptedAt = Instant.now();
    }
}
