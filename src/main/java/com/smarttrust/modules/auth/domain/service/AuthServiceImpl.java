package com.smarttrust.modules.auth.domain.service;

import com.smarttrust.common.exception.BusinessException;
import com.smarttrust.common.exception.ErrorCode;
import com.smarttrust.common.mail.EmailService;
import com.smarttrust.common.security.JwtTokenProvider;
import com.smarttrust.common.security.SecurityUtils;
import com.smarttrust.modules.auth.api.dto.*;
import com.smarttrust.modules.auth.domain.entity.LoginAttempt;
import com.smarttrust.modules.auth.domain.entity.OtpCode;
import com.smarttrust.modules.auth.domain.enums.OtpPurpose;
import com.smarttrust.modules.auth.domain.event.UserLoggedInEvent;
import com.smarttrust.modules.auth.domain.event.UserRegisteredEvent;
import com.smarttrust.modules.auth.infrastructure.persistence.LoginAttemptRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${smarttrust.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${smarttrust.otp.mock-enabled:false}")
    private boolean otpMockEnabled;

    @Value("${smarttrust.security.max-failed-login-attempts:5}")
    private int maxFailedAttempts;

    @Value("${smarttrust.security.lock-time-minutes:15}")
    private int lockTimeMinutes;

    // ---- REGISTER INIT (email + phone; OTP delivered by EMAIL) ----

    @Override
    @Transactional
    public OtpSentResponse registerInit(RegisterInitRequest request, HttpServletRequest httpRequest) {
        String phone = normalizePhone(request.phone());
        String email = normalizeEmail(request.email());

        // Phone unique (deleted users may re-register)
        if (userRepository.existsByPhone(phone)) {
            var existing = userRepository.findByPhone(phone);
            if (existing.isPresent() && existing.get().getStatus() != UserStatus.DELETED) {
                throw BusinessException.of(ErrorCode.AUTH_PHONE_ALREADY_EXISTS, HttpStatus.CONFLICT,
                        "Phone already registered: " + phone);
            }
        }

        // Email unique (deleted users may re-register)
        if (userRepository.existsByEmail(email)) {
            var existingByEmail = userRepository.findByEmail(email);
            if (existingByEmail.isPresent() && existingByEmail.get().getStatus() != UserStatus.DELETED) {
                throw BusinessException.of(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS, HttpStatus.CONFLICT,
                        "Email already registered");
            }
        }

        // Validate role: ADMIN not allowed via public registration
        if (request.role() == UserRole.ADMIN) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Admin registration not allowed via public API");
        }

        User user = User.builder()
                .phone(phone)
                .email(email)
                .fullName(request.fullName())
                .phoneVerified(false)
                .emailVerified(false)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role()) // nullable since v2 — selected after OTP verification
                .status(UserStatus.PENDING)
                .build();

        User savedUser = userRepository.save(user);

        // OTP: hashed at rest, emailed to user, old codes superseded, NEVER in the API response
        var otp = otpService.generateOtp(phone, email, OtpPurpose.REGISTRATION);
        deliverOtpByEmail(email, otp.plainOtp(), "Registration");

        eventPublisher.publishEvent(new UserRegisteredEvent(this, savedUser.getId(), phone,
                savedUser.getRole() != null ? savedUser.getRole().name() : "UNSELECTED", null));

        log.info("Register init OK phone={} email={} userId={}", phone, EmailService.maskEmail(email), savedUser.getId());

        return new OtpSentResponse(
                savedUser.getId(),
                phone,
                EmailService.maskEmail(email),
                "Verification code sent to your email. Verify within " + otpExpiryMinutes + " minutes.",
                otp.entity().getExpiresAt(),
                otp.resendAvailableAt()
        );
    }

    // ---- RESEND OTP (cooldown enforced) ----

    @Override
    @Transactional
    public OtpSentResponse resendRegistrationOtp(ResendOtpRequest request) {
        String phone = normalizePhone(request.phone());

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "User not found"));

        if (user.getStatus() != UserStatus.PENDING) {
            throw BusinessException.of(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                    "Account already verified. Please login.");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw BusinessException.of(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                    "No email on file for this account. Please register again.");
        }

        // Cooldown: latest OTP must be older than resend-cooldown-seconds
        var latest = otpService.findLatestOtp(phone, OtpPurpose.REGISTRATION);
        if (latest.isPresent()) {
            Instant availableAt = latest.get().getCreatedAt()
                    .plus(otpServiceCooldownSeconds(), ChronoUnit.SECONDS);
            if (Instant.now().isBefore(availableAt)) {
                long wait = java.time.Duration.between(Instant.now(), availableAt).getSeconds() + 1;
                throw BusinessException.of(ErrorCode.AUTH_OTP_RESEND_COOLDOWN, HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait " + wait + " seconds before requesting a new code.");
            }
        }

        var otp = otpService.generateOtp(phone, user.getEmail(), OtpPurpose.REGISTRATION); // supersedes old OTP
        deliverOtpByEmail(user.getEmail(), otp.plainOtp(), "Registration resend");

        return new OtpSentResponse(
                user.getId(),
                phone,
                EmailService.maskEmail(user.getEmail()),
                "New verification code sent to your email.",
                otp.entity().getExpiresAt(),
                otp.resendAvailableAt()
        );
    }

    // ---- VERIFY OTP (activates account + marks email verified) ----

    @Override
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request, HttpServletRequest httpRequest) {
        String phone = normalizePhone(request.phone());

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "User not found"));

        if (user.getStatus() == UserStatus.DELETED) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found");
        }

        OtpCode otpCode = otpService.findActiveOtp(phone, OtpPurpose.REGISTRATION)
                .orElseThrow(() -> BusinessException.of(ErrorCode.AUTH_OTP_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "No active OTP found. Please request a new OTP."));

        otpService.verifyOtp(otpCode, request.otp()); // throws on invalid/expired/max attempts
        otpService.markVerified(otpCode);             // single-use: consumed

        user.activate();              // status ACTIVE + phoneVerified
        user.setEmailVerified(true);  // email verified — this was an EMAIL OTP
        userRepository.save(user);

        String ip = getClientIp(httpRequest);
        var refreshResult = refreshTokenService.createRefreshToken(user.getId(), ip, "verify-otp");
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(),
                roleOrNull(user), user.getStatus().name());

        log.info("User email-verified and activated phone={} userId={}", phone, user.getId());

        return buildAuthResponse(user, accessToken, refreshResult.rawToken(), refreshResult.entity().getExpiresAt());
    }

    // ---- SELECT ROLE (after email verification; CUSTOMER or SERVICE_PROVIDER) ----

    @Override
    @Transactional
    public AuthResponse selectRole(SelectRoleRequest request, HttpServletRequest httpRequest) {
        Long userId = currentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw BusinessException.of(ErrorCode.AUTH_EMAIL_NOT_VERIFIED, HttpStatus.FORBIDDEN,
                    "Verify your email before selecting a role.");
        }
        if (request.role() == UserRole.ADMIN) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "ADMIN cannot be selected via this endpoint.");
        }
        if (user.getRole() != null) {
            if (user.getRole() == request.role()) {
                throw BusinessException.of(ErrorCode.AUTH_ROLE_ALREADY_SELECTED, HttpStatus.CONFLICT,
                        "Role already selected: " + user.getRole());
            }
            throw BusinessException.of(ErrorCode.AUTH_ROLE_ALREADY_SELECTED, HttpStatus.CONFLICT,
                    "Role already selected (" + user.getRole() + ") and cannot be changed. Contact support.");
        }

        user.setRole(request.role());
        userRepository.save(user);

        // Issue fresh tokens so the new JWT carries the role claim
        String ip = getClientIp(httpRequest);
        var refreshResult = refreshTokenService.createRefreshToken(user.getId(), ip, "select-role");
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(),
                user.getRole().name(), user.getStatus().name());

        log.info("Role selected userId={} role={}", user.getId(), user.getRole());

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
            log.warn("Account locked (brute force) phone={} ip={} failedCount={}", phone, ip, failedCount);
            throw BusinessException.of(ErrorCode.AUTH_ACCOUNT_LOCKED, HttpStatus.LOCKED,
                    "Account locked due to too many failed attempts. Try again after " + lockTimeMinutes + " minutes.");
        }

        User user = userRepository.findByPhone(phone).orElse(null);

        boolean passwordMatches = false;
        if (user != null) {
            passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        }

        if (user == null || !passwordMatches) {
            loginAttemptRepository.save(LoginAttempt.builder()
                    .phone(phone)
                    .ipAddress(ip)
                    .success(false)
                    .attemptedAt(Instant.now())
                    .build());
            throw BusinessException.of(ErrorCode.AUTH_INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
                    "Invalid phone or password");
        }

        if (user.getStatus() == UserStatus.PENDING) {
            throw BusinessException.of(ErrorCode.AUTH_PHONE_NOT_VERIFIED, HttpStatus.FORBIDDEN,
                    "Email not verified. Please verify your account with the OTP sent to your email.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw BusinessException.of(ErrorCode.AUTH_ACCOUNT_SUSPENDED, HttpStatus.FORBIDDEN,
                    "Account suspended. Contact admin.");
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw BusinessException.of(ErrorCode.AUTH_ACCOUNT_BANNED, HttpStatus.FORBIDDEN, "Account banned.");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found");
        }

        loginAttemptRepository.save(LoginAttempt.builder()
                .phone(phone)
                .ipAddress(ip)
                .success(true)
                .attemptedAt(Instant.now())
                .build());

        user.recordLogin();
        userRepository.save(user);

        var refreshResult = refreshTokenService.createRefreshToken(user.getId(), ip, request.deviceInfo());
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(),
                roleOrNull(user), user.getStatus().name());

        eventPublisher.publishEvent(new UserLoggedInEvent(this, user.getId(), phone, ip));

        log.info("Login success phone={} userId={} ip={}", phone, user.getId(), ip);

        return buildAuthResponse(user, accessToken, refreshResult.rawToken(), refreshResult.entity().getExpiresAt());
    }

    // ---- REFRESH ----

    @Override
    @Transactional
    public AuthResponse refresh(RefreshRequest request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);

        var oldToken = refreshTokenService.findByRawTokenOrThrow(request.refreshToken());

        User user = userRepository.findById(oldToken.getUserId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "User not found"));

        if (!user.isActive()) {
            throw BusinessException.of(ErrorCode.AUTH_ACCOUNT_NOT_ACTIVE, HttpStatus.FORBIDDEN, "User not active");
        }

        var newResult = refreshTokenService.rotateRefreshToken(oldToken, ip, "refresh");
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getPhone(),
                roleOrNull(user), user.getStatus().name());

        log.info("Refresh rotation userId={} oldId={} newId={}",
                user.getId(), oldToken.getId(), newResult.entity().getId());

        return buildAuthResponse(user, newAccessToken, newResult.rawToken(), newResult.entity().getExpiresAt());
    }

    @Override
    @Transactional
    public void logout(RefreshRequest request, HttpServletRequest httpRequest) {
        try {
            var token = refreshTokenService.findByRawTokenOrThrow(request.refreshToken());
            refreshTokenService.revokeToken(token);
            log.info("Logout success userId={}", token.getUserId());
        } catch (BusinessException ex) {
            // Idempotent logout: invalid token still returns 204 (don't leak token state)
            log.warn("Logout with invalid token: {}", ex.getMessage());
        }
    }

    // ---- FORGOT PASSWORD (OTP delivered by EMAIL) ----

    @Override
    @Transactional
    public OtpSentResponse forgotPasswordInit(ForgotPasswordInitRequest request) {
        String phone = normalizePhone(request.phone());

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "User not found with phone: " + phone));

        if (user.getStatus() == UserStatus.DELETED) {
            throw BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw BusinessException.of(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                    "No email on file. Cannot send reset code.");
        }

        var otp = otpService.generateOtp(phone, user.getEmail(), OtpPurpose.FORGOT_PASSWORD);
        deliverOtpByEmail(user.getEmail(), otp.plainOtp(), "Forgot password");

        return new OtpSentResponse(
                user.getId(),
                phone,
                EmailService.maskEmail(user.getEmail()),
                "Password reset code sent to your email. Valid for " + otpExpiryMinutes + " minutes.",
                otp.entity().getExpiresAt(),
                otp.resendAvailableAt()
        );
    }

    @Override
    @Transactional
    public void forgotPasswordReset(ForgotPasswordResetRequest request) {
        String phone = normalizePhone(request.phone());

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "User not found"));

        OtpCode otpCode = otpService.findActiveOtp(phone, OtpPurpose.FORGOT_PASSWORD)
                .orElseThrow(() -> BusinessException.of(ErrorCode.AUTH_OTP_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "No active reset code found. Please request a new one."));

        otpService.verifyOtp(otpCode, request.otp());
        otpService.markVerified(otpCode);

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Security: revoke all refresh tokens to force re-login on all devices
        refreshTokenService.revokeAllByUserId(user.getId());

        log.info("Password reset success phone={} userId={}", phone, user.getId());
    }

    // ---- HELPERS ----

    private void deliverOtpByEmail(String email, String plainOtp, String flowLabel) {
        boolean sent = emailService.sendOtpEmail(email, plainOtp, otpExpiryMinutes);
        if (!sent && !otpMockEnabled) {
            // Mail could not be delivered (SMTP not configured / failure). User can use resend once fixed.
            log.error("OTP email delivery FAILED for flow={} recipient={} — check SMTP configuration (SMTP_HOST/SMTP_USERNAME/SMTP_PASSWORD)",
                    flowLabel, EmailService.maskEmail(email));
        }
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserId();
    }

    private static String roleOrNull(User user) {
        return user.getRole() != null ? user.getRole().name() : null;
    }

    private int otpServiceCooldownSeconds() {
        return otpService.getResendCooldownSeconds();
    }

    private String normalizePhone(String phone) {
        if (phone == null) return null;
        phone = phone.trim();
        if (phone.startsWith("+923") && phone.length() == 13) {
            return "0" + phone.substring(3); // +923001234567 -> 03001234567
        }
        return phone;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken, Instant refreshExpiresAt) {
        return new AuthResponse(
                user.getId(),
                user.getPhone(),
                roleOrNull(user),
                user.getStatus().name(),
                accessToken,
                refreshToken,
                900, // access token lifetime in seconds (15 min)
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
