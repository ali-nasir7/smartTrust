package com.smarttrust.modules.auth.api;

import com.smarttrust.modules.auth.api.dto.*;
import com.smarttrust.modules.auth.domain.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth Module", description = "Registration (email OTP), Resend OTP, Verify, Role Selection, Login, Refresh, Logout, Forgot Password")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register Init — phone + email + password, OTP sent to EMAIL",
            description = "Creates a PENDING user and emails a 6-digit verification code (hashed at rest, 5min expiry, "
                    + "max 3 attempts, 60s resend cooldown). The OTP is NEVER returned in the API response. "
                    + "Role is chosen AFTER verification via /select-role (sending role here is still supported for old clients).")
    @PostMapping("/register/init")
    public ResponseEntity<OtpSentResponse> registerInit(
            @Valid @RequestBody RegisterInitRequest request,
            HttpServletRequest httpRequest) {

        log.info("POST /auth/register/init phone={} email=***", request.phone());
        var response = authService.registerInit(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Resend registration OTP to email",
            description = "Sends a new OTP to the user's email. Enforces resend cooldown (default 60s) and "
                    + "invalidates the previous OTP immediately.")
    @PostMapping("/register/resend-otp")
    public ResponseEntity<OtpSentResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        log.info("POST /auth/register/resend-otp phone={}", request.phone());
        return ResponseEntity.ok(authService.resendRegistrationOtp(request));
    }

    @Operation(summary = "Verify Email OTP — activates account + issues JWT",
            description = "Verifies the 6-digit code from email. On success: email_verified=true, status ACTIVE, "
                    + "issues accessToken (15m) + refreshToken (7d, rotation). If no role was sent at registration, "
                    + "the client must next call /select-role.")
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest) {

        log.info("POST /auth/verify-otp phone={}", request.phone());
        return ResponseEntity.ok(authService.verifyOtp(request, httpRequest));
    }

    @Operation(summary = "Select role (CUSTOMER or SERVICE_PROVIDER) — after email verification",
            description = "Authenticated endpoint. Sets the user's role and returns FRESH tokens whose JWT "
                    + "carries the role claim. One-time only; ADMIN cannot be selected here.")
    @PostMapping("/select-role")
    public ResponseEntity<AuthResponse> selectRole(
            @Valid @RequestBody SelectRoleRequest request,
            HttpServletRequest httpRequest) {

        log.info("POST /auth/select-role role={}", request.role());
        return ResponseEntity.ok(authService.selectRole(request, httpRequest));
    }

    @Operation(summary = "Login with phone + password",
            description = "Brute-force protected: max 5 failed attempts per 15 min per phone → account locked 423. "
                    + "Role may be null (user must complete /select-role).")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        log.info("POST /auth/login phone={}", request.phone());
        return ResponseEntity.ok(authService.login(request, httpRequest));
    }

    @Operation(summary = "Refresh access token with rotation",
            description = "Uses opaque refresh token (SHA-256 hashed in DB). Validates not revoked/expired, rotates.")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {

        log.info("POST /auth/refresh");
        return ResponseEntity.ok(authService.refresh(request, httpRequest));
    }

    @Operation(summary = "Logout — revoke refresh token (idempotent)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {

        log.info("POST /auth/logout");
        authService.logout(request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Forgot password init — OTP sent to EMAIL",
            description = "Sends a password-reset code to the registered email address.")
    @PostMapping("/forgot-password/init")
    public ResponseEntity<OtpSentResponse> forgotPasswordInit(
            @Valid @RequestBody ForgotPasswordInitRequest request) {

        log.info("POST /auth/forgot-password/init phone={}", request.phone());
        return ResponseEntity.ok(authService.forgotPasswordInit(request));
    }

    @Operation(summary = "Forgot password reset — verify OTP + new password",
            description = "Verifies the emailed code and resets the password. Revokes all refresh tokens (force re-login).")
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<Void> forgotPasswordReset(
            @Valid @RequestBody ForgotPasswordResetRequest request) {

        log.info("POST /auth/forgot-password/reset phone={}", request.phone());
        authService.forgotPasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Health check for auth module")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth module UP — SmartTrust v2");
    }
}
