package com.smarttrust.modules.auth.infrastructure.persistence;

import com.smarttrust.modules.auth.domain.entity.OtpCode;
import com.smarttrust.modules.auth.domain.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    // Find latest OTP for phone + purpose that is not verified, ordered by created_at desc
    Optional<OtpCode> findTopByPhoneAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(String phone, OtpPurpose purpose);

    // For any purpose
    Optional<OtpCode> findTopByPhoneOrderByCreatedAtDesc(String phone);
}
