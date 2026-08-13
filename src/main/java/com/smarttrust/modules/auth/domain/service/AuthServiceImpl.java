package com.smarttrust.modules.auth.domain.service;

import com.smarttrust.common.exception.BusinessException;
import com.smarttrust.common.exception.ErrorCode;
import com.smarttrust.common.security.JwtTokenProvider;
import com.smarttrust.modules.auth.api.dto.*;
import com.smarttrust.modules.auth.domain.entity.LoginAttempt;
import com.smarttrust.modules.auth.domain.entity.OtpCode;
import com.smarttrust.modules.auth.domain.enums.OtpPurpose;
import com.smarttrust.modules.auth.domain.event.UserLoggedInEvent;
import com.smarttrust.modules.auth.domain.event.UserRegisteredEvent;
import com.smarttrust.modules.auth.infrastructure.persistence.LoginAttemptRepository;
import com.smarttrust.modules.auth.infrastructure.persistence.OtpCodeRepository;
import com.smarttrust.modules.user.domain.entity.User;
import com.smarttrust.modules.user.domain.enums.UserRole;
import com.smarttrust.modules.user.domain.enums.UserStatus;
import com.smarttrust.modules.user.infrastructure.persistence.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${smarttrust.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${smarttrust.otp.max-attempts:3}")
    private int otpMaxAttempts;

    @Value("${smarttrust.otp.mock-enabled:true}")
    private boolean otpMockEnabled;

    @Value("${smarttrust.security.max-failed-login-attempts:5}")
    private int maxFailedAttempts;

    @Value("${smarttrust.security.lock-time-minutes:15}")
    private int lockTimeMinutes;

    private final SecureRandom secureRandom = new SecureRandom();

    // ---- REGISTER INIT ----

    @Override
    @Transactional
    public RegisterInitResponse registerInit(RegisterInitRequest request, HttpServletRequest httpRequest) {
        String phone = normalizePhone(request.phone());

        // Check phone unique
        if (userRepository.existsByPhone(phone)) {
            // Check if user exists but status DELETED? Our exists includes deleted, but we allow reuse if deleted?
            var existing = userRepository.findByPhone(phone);
            if (existing.isPresent() && existing.get().getStatus() != UserStatus.DELETED) {
                throw BusinessException.of(ErrorCode.AUTH_PHONE_ALREADY_EXISTS, HttpStatus.CONFLICT,
                        "Phone already registered: " + phone);
            }
        }

        // Validate role: ADMIN not allowed via public registration (only dev)
        if (request.role() == UserRole.ADMIN) {
            // For FYP, allow ADMIN only if env var? We'll block for security; use separate seed.
            throw BusinessException.of(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Admin registration not allowed via public API");
        }

        // Hash password
        String passwordHash = passwordEncoder.encode(request.password());

        User user = User.builder()
                .phone(phone)
                .phoneVerified(false)
                .passwordHash(passwordHash)
                .role(request.role())
                .status(UserStatus.PENDING)
                .build();

        User savedUser = userRepository.save(user);

        // Generate OTP
        String plainOtp = generateOtp();
        String otpHash = passwordEncoder.encode(plainOtp);

        OtpCode otpCode = OtpCode.builder()
                .phone(phone)
                .otpHash(otpHash)
                .purpose(OtpPurpose.REGISTRATION)
                .expiresAt(Instant.now().plus(otpExpiryMinutes, ChronoUnit.MINUTES))
                .attempts(0)
                .verified(false)
                .build();

        otpCodeRepository.save(otpCode);

        log.info("📲 REGISTER OTP phone={} otp={} (mock, expires in {} min) userId={}",
                phone, plainOtp, otpExpiryMinutes, savedUser.getId());

        // Publish event
        eventPublisher.publishEvent(new UserRegisteredEvent(this, savedUser.getId(), phone, savedUser.getRole().name(), plainOtp));

        return new RegisterInitResponse(
                savedUser.getId(),
                phone,
                savedUser.getRole().name(),
                "OTP sent to phone. Verify within " + otpExpiryMinutes + " minutes.",
                otpCode.getExpiresAt(),
                otpMockEnabled ? plainOtp : null // Only for FYP testing
        );
    }

    // ---- VERIFY OTP ----

    @Override
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request, HttpServletRequest httpRequest) {
        String phone = normalizePhone(request.phone());
        String plainOtp = request.otp();

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));

        if (user.getStatus() == UserStatus.DELETED) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found");
        }

        OtpCode otpCode = otpCodeRepository.findTopByPhoneAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(phone, OtpPurpose.REGISTRATION)
                .orElseThrow(() -> BusinessException.of(ErrorCode.AUTH_OTP_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "No valid OTP found. Please request new OTP."));

        // Check expiry
        if (otpCode.isExpired()) {
            throw BusinessException.of(ErrorCode.AUTH_OTP_EXPIRED, HttpStatus.BAD_REQUEST, "OTP expired. Please request new one.");
        }
        if (otpCode.isMaxAttemptsReached(otpMaxAttempts)) {
            throw BusinessException.of(ErrorCode.AUTH_OTP_MAX_ATTEMPTS, HttpStatus.TOO_MANY_REQUESTS,
                    "Max attempts reached. Request new OTP.");
        }

        // Verify hash
        if (!passwordEncoder.matches(plainOtp, otpCode.getOtpHash())) {
            otpCode.incrementAttempts();
            otpCodeRepository.save(otpCode);
            throw BusinessException.of(ErrorCode.AUTH_OTP_INVALID, HttpStatus.BAD_REQUEST,
                    "Invalid OTP. Attempts: " + otpCode.getAttempts() + "/" + otpMaxAttempts);
        }

        // Success
        otpCode.markVerified();
        otpCodeRepository.save(otpCode);

        user.activate(); // sets status ACTIVE + phoneVerified true
        userRepository.save(user);

        // Generate tokens
        String ip = getClientIp(httpRequest);
        var refreshResult = refreshTokenService.createRefreshToken(user.getId(), ip, "verify-otp");

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(), user.getRole().name(), user.getStatus().name());

        log.info("✅ User verified phone={} userId={}", phone, user.getId());

        return buildAuthResponse(user, accessToken, refreshResult.rawToken(), refreshResult.entity().getExpiresAt());
    }

    // ---- LOGIN ----

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String phone = normalizePhone(request.phone());
        String ip = getClientIp(httpRequest);

        // Brute-force check BEFORE verifying password
        Instant lockWindow = Instant.now().minus(lockTimeMinutes, ChronoUnit.MINUTES);
        long failedCount = loginAttemptRepository.countByPhoneAndSuccessFalseAndAttemptedAtAfter(phone, lockWindow);

        if (failedCount >= maxFailedAttempts) {
            log.warn("🔒 Account locked due to brute force phone={} ip={} failedCount={}", phone, ip, failedCount);
            throw BusinessException.of(ErrorCode.AUTH_ACCOUNT_LOCKED, HttpStatus.LOCKED,
                    "Account locked due to too many failed attempts. Try again after " + lockTimeMinutes + " minutes.");
        }

        User user = userRepository.findByPhone(phone)
                .orElse(null);

        boolean passwordMatches = false;
        if (user != null) {
            passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        }

        if (user == null || !passwordMatches) {
            // Record failed attempt
            loginAttemptRepository.save(LoginAttempt.builder()
                    .phone(phone)
                    .ipAddress(ip)
                    .success(false)
                    .attemptedAt(Instant.now())
                    .build());

            throw BusinessException.of(ErrorCode.AUTH_INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                    "Invalid phone or password");
        }

        // Check status
        if (user.getStatus() == UserStatus.PENDING) {
            throw BusinessException.of(ErrorCode.AUTH_PHONE_NOT_VERIFIED, HttpStatus.FORBIDDEN,
                    "Phone not verified. Please verify OTP.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw BusinessException.of(ErrorCode.AUTH_ACCOUNT_SUSPENDED, HttpStatus.FORBIDDEN,
                    "Account suspended. Contact admin.");
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw BusinessException.of(ErrorCode.AUTH_ACCOUNT_BANNED, HttpStatus.FORBIDDEN,
                    "Account banned.");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found");
        }

        // Success - record attempt
        loginAttemptRepository.save(LoginAttempt.builder()
                .phone(phone)
                .ipAddress(ip)
                .success(true)
                .attemptedAt(Instant.now())
                .build());

        user.recordLogin();
        userRepository.save(user);

        // Generate tokens
        var refreshResult = refreshTokenService.createRefreshToken(user.getId(), ip, request.deviceInfo());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(), user.getRole().name(), user.getStatus().name());

        eventPublisher.publishEvent(new UserLoggedInEvent(this, user.getId(), phone, ip));

        log.info("✅ Login success phone={} userId={} ip={}", phone, user.getId(), ip);

        return buildAuthResponse(user, accessToken, refreshResult.rawToken(), refreshResult.entity().getExpiresAt());
    }

    // ---- REFRESH ----

    @Override
    @Transactional
    public AuthResponse refresh(RefreshRequest request, HttpServletRequest httpRequest) {
        String rawToken = request.refreshToken();
        String ip = getClientIp(httpRequest);

        var oldToken = refreshTokenService.findByRawTokenOrThrow(rawToken);

        User user = userRepository.findById(oldToken.getUserId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));

        if (!user.isActive()) {
            throw BusinessException.of(ErrorCode.AUTH_ACCOUNT_NOT_ACTIVE, HttpStatus.FORBIDDEN, "User not active");
        }

        // Rotation
        var newResult = refreshTokenService.rotateRefreshToken(oldToken, ip, "refresh");

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(), user.getRole().name(), user.getStatus().name());

        log.info("🔄 Refresh rotation userId={} oldId={} newId={}", user.getId(), oldToken.getId(), newResult.entity().getId());

        return buildAuthResponse(user, newAccessToken, newResult.rawToken(), newResult.entity().getExpiresAt());
    }

    @Override
    @Transactional
    public void logout(RefreshRequest request, HttpServletRequest httpRequest) {
        try {
            var token = refreshTokenService.findByRawTokenOrThrow(request.refreshToken());
            refreshTokenService.revokeToken(token);
            log.info("🚪 Logout success userId={}", token.getUserId());
        } catch (BusinessException ex) {
            // Idempotent logout: if token invalid, still return success (don't leak)
            log.warn("Logout with invalid token: {}", ex.getMessage());
        }
    }

    // ---- FORGOT PASSWORD ----

    @Override
    @Transactional
    public RegisterInitResponse forgotPasswordInit(ForgotPasswordInitRequest request) {
        String phone = normalizePhone(request.phone());

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found with phone: " + phone));

        if (user.getStatus() == UserStatus.DELETED) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found");
        }

        String plainOtp = generateOtp();
        String otpHash = passwordEncoder.encode(plainOtp);

        OtpCode otpCode = OtpCode.builder()
                .phone(phone)
                .otpHash(otpHash)
                .purpose(OtpPurpose.FORGOT_PASSWORD)
                .expiresAt(Instant.now().plus(otpExpiryMinutes, ChronoUnit.MINUTES))
                .attempts(0)
                .verified(false)
                .build();

        otpCodeRepository.save(otpCode);

        log.info("🔐 FORGOT PASSWORD OTP phone={} otp={} userId={}", phone, plainOtp, user.getId());

        return new RegisterInitResponse(
                user.getId(),
                phone,
                user.getRole().name(),
                "OTP sent for password reset. Valid for " + otpExpiryMinutes + " minutes.",
                otpCode.getExpiresAt(),
                otpMockEnabled ? plainOtp : null
        );
    }

    @Override
    @Transactional
    public void forgotPasswordReset(ForgotPasswordResetRequest request) {
        String phone = normalizePhone(request.phone());
        String plainOtp = request.otp();

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));

        OtpCode otpCode = otpCodeRepository.findTopByPhoneAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(phone, OtpPurpose.FORGOT_PASSWORD)
                .orElseThrow(() -> BusinessException.of(ErrorCode.AUTH_OTP_NOT_FOUND, HttpStatus.NOT_FOUND, "No OTP found"));

        if (otpCode.isExpired()) {
            throw BusinessException.of(ErrorCode.AUTH_OTP_EXPIRED, HttpStatus.BAD_REQUEST, "OTP expired");
        }
        if (otpCode.isMaxAttemptsReached(otpMaxAttempts)) {
            throw BusinessException.of(ErrorCode.AUTH_OTP_MAX_ATTEMPTS, HttpStatus.TOO_MANY_REQUESTS, "Max attempts reached");
        }
        if (!passwordEncoder.matches(plainOtp, otpCode.getOtpHash())) {
            otpCode.incrementAttempts();
            otpCodeRepository.save(otpCode);
            throw BusinessException.of(ErrorCode.AUTH_OTP_INVALID, HttpStatus.BAD_REQUEST, "Invalid OTP");
        }

        // Valid -> reset password
        otpCode.markVerified();
        otpCodeRepository.save(otpCode);

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Security: revoke all refresh tokens to force re-login on all devices
        refreshTokenService.revokeAllByUserId(user.getId());

        log.info("✅ Password reset success phone={} userId={}", phone, user.getId());
    }

    // ---- HELPERS ----

    private String generateOtp() {
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }

    private String normalizePhone(String phone) {
        if (phone == null) return null;
        phone = phone.trim();
        // Convert 03xx to +923xx? For MVP keep as is, but normalize to 03 format? We'll just keep.
        // If starts with +923, convert to 03 for consistency? Let's keep both but unique check should handle both?
        // For simplicity, if +923... -> convert to 03...
        if (phone.startsWith("+923") && phone.length() == 13) {
            return "0" + phone.substring(3); // +923001234567 -> 03001234567
        }
        return phone;
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken, Instant refreshExpiresAt) {
        return new AuthResponse(
                user.getId(),
                user.getPhone(),
                user.getRole().name(),
                user.getStatus().name(),
                accessToken,
                refreshToken,
                900, // 15min in seconds, could be from config
                Instant.now().plus(15, ChronoUnit.MINUTES),
                refreshExpiresAt
        );
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
