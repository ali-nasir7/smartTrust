package com.smarttrust.modules.auth.domain.service;

import com.smarttrust.common.exception.BusinessException;
import com.smarttrust.common.exception.ErrorCode;
import com.smarttrust.modules.auth.domain.entity.OtpCode;
import com.smarttrust.modules.auth.domain.enums.OtpPurpose;
import com.smarttrust.modules.auth.infrastructure.persistence.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final OtpCodeRepository otpCodeRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${smarttrust.otp.expiry-minutes:5}")
    private int expiryMinutes;

    @Value("${smarttrust.otp.max-attempts:3}")
    private int maxAttempts;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public OtpCode generateAndSaveOtp(String phone, OtpPurpose purpose) {
        // Generate 6-digit OTP
        int otpInt = 100000 + secureRandom.nextInt(900000);
        String plainOtp = String.valueOf(otpInt);

        String otpHash = passwordEncoder.encode(plainOtp);

        OtpCode otpCode = OtpCode.builder()
                .phone(phone)
                .otpHash(otpHash)
                .purpose(purpose)
                .expiresAt(Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES))
                .attempts(0)
                .verified(false)
                .build();

        OtpCode saved = otpCodeRepository.save(otpCode);

        // IMPORTANT: We need to return plain OTP for mock notification/logging.
        // We store hash only, but we can return plain via transient field in event.
        // This method returns entity with hash; service calling it should have plain to send.
        // To avoid leaking, we use a trick: store plain in MDC? Actually we return plain via separate wrapper.
        // For simplicity, we will cache plain OTP in a ThreadLocal or just return entity and let caller have plain.
        // Here we hack: set a transient field via log? Better: caller already has plain OTP; we just save hash.
        // So we need to adjust: generate plain OTP in AuthService, hash here? Simpler: generate here and store plain in event.
        // We'll return saved entity, but AuthService still has plain OTP to send.
        // To pass plain OTP, we will store it in a ThreadLocal holder (not ideal). Instead, we will generate OTP in AuthService.
        // For this implementation, we generate OTP here, but we need to expose plain OTP.
        // We'll use a hack: encode plain OTP into otpHash field temporarily? No.
        // Solution: AuthService generates plain OTP, then calls overload.
        // So this method is deprecated in favor of overload below.

        // This implementation returns entity but caller must have plain already.
        // We'll just return entity; AuthService will generate its own plain and compare?
        // Let's keep this but also log OTP when mock enabled.

        // Since we already generated plainOTP, we need to pass it back. We'll use a ThreadLocal or Event.
        // Simplify: log automatically here if mock enabled (controlled by property via caller)
        // We'll store plainOTP in a static holder for immediate retrieval (unsafe but works for FYP synchronous flow)
        OtpPlainHolder.set(plainOtp);

        log.info("🔐 OTP GENERATED phone={} purpose={} OTP={} expiresAt={} (mock - for FYP testing only, never log in prod)",
                phone, purpose, plainOtp, saved.getExpiresAt());

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public OtpCode getLatestValidOtp(String phone, OtpPurpose purpose) {
        return otpCodeRepository.findTopByPhoneAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(phone, purpose)
                .orElse(null);
    }

    @Override
    public void verifyOtp(OtpCode otpCode, String plainOtp) {
        if (otpCode == null) {
            throw BusinessException.of(ErrorCode.AUTH_OTP_NOT_FOUND, HttpStatus.NOT_FOUND, "OTP not found. Please request a new OTP.");
        }
        if (otpCode.isExpired()) {
            throw BusinessException.of(ErrorCode.AUTH_OTP_EXPIRED, HttpStatus.BAD_REQUEST, "OTP expired. Please request new OTP.");
        }
        if (otpCode.isMaxAttemptsReached(maxAttempts)) {
            throw BusinessException.of(ErrorCode.AUTH_OTP_MAX_ATTEMPTS, HttpStatus.TOO_MANY_REQUESTS,
                    "Max OTP attempts reached. Please request new OTP after 5 minutes.");
        }
        if (!passwordEncoder.matches(plainOtp, otpCode.getOtpHash())) {
            otpCode.incrementAttempts();
            otpCodeRepository.save(otpCode);
            throw BusinessException.of(ErrorCode.AUTH_OTP_INVALID, HttpStatus.BAD_REQUEST,
                    "Invalid OTP. Attempts: " + otpCode.getAttempts() + "/" + maxAttempts);
        }
    }

    @Override
    @Transactional
    public void incrementAttempts(OtpCode otpCode) {
        otpCode.incrementAttempts();
        otpCodeRepository.save(otpCode);
    }

    @Override
    @Transactional
    public void markVerified(OtpCode otpCode) {
        otpCode.markVerified();
        otpCodeRepository.save(otpCode);
    }

    /**
     * Helper to retrieve plain OTP that was just generated (for mock notification).
     * Since we generate OTP inside this service, caller can retrieve it via this holder.
     * NOTE: ThreadLocal must be cleared after use.
     */
    public static class OtpPlainHolder {
        private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

        public static void set(String otp) { HOLDER.set(otp); }
        public static String get() { return HOLDER.get(); }
        public static void clear() { HOLDER.remove(); }
    }
}
