package com.smarttrust.modules.auth.infrastructure.persistence;

import com.smarttrust.modules.auth.domain.entity.OtpCode;
import com.smarttrust.modules.auth.domain.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    /** Latest OTP for phone + purpose that is not verified AND not superseded (i.e. the active one). */
    Optional<OtpCode> findTopByPhoneAndPurposeAndVerifiedFalseAndSupersededAtIsNullOrderByCreatedAtDesc(
            String phone, OtpPurpose purpose);

    /** Latest OTP row (any state) — used to enforce the resend cooldown. */
    Optional<OtpCode> findTopByPhoneAndPurposeOrderByCreatedAtDesc(String phone, OtpPurpose purpose);

    /**
     * Invalidates all active (not verified, not superseded) OTPs for phone+purpose.
     * Called right before a new OTP is generated, so the old code stops working immediately.
     */
    @Modifying
    @Query("UPDATE OtpCode o SET o.supersededAt = :now " +
           "WHERE o.phone = :phone AND o.purpose = :purpose " +
           "AND o.verified = false AND o.supersededAt IS NULL")
    int supersedeActiveOtps(@Param("phone") String phone,
                            @Param("purpose") OtpPurpose purpose,
                            @Param("now") Instant now);
}
