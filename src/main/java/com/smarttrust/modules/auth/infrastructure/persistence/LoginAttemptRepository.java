package com.smarttrust.modules.auth.infrastructure.persistence;

import com.smarttrust.modules.auth.domain.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    long countByPhoneAndSuccessFalseAndAttemptedAtAfter(String phone, Instant after);

    long countByIpAddressAndSuccessFalseAndAttemptedAtAfter(String ip, Instant after);

    // Cleanup old
    void deleteByAttemptedAtBefore(Instant before);
}
