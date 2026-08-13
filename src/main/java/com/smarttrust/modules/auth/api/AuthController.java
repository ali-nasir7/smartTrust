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
@Tag(name = "Auth Module", description = "Registration, OTP verification, Login, Refresh, Logout, Forgot Password — Modular Monolith Auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register Init — Step 1: Create user + send OTP",
            description = "Customer/Provider registration init. Validates phone unique, creates PENDING user, generates 6-digit OTP hashed with BCrypt (5min expiry, max 3 attempts). Mock OTP returned in dev for FYP.")
    @PostMapping("/register/init")
    public ResponseEntity<RegisterInitResponse> registerInit(
            @Valid @RequestBody RegisterInitRequest request,
            HttpServletRequest httpRequest) {

        log.info("POST /auth/register/init phone={} role={}", request.phone(), request.role());
        var response = authService.registerInit(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Verify OTP — Step 2: Activate account + issue JWT",
            description = "Verifies 6-digit OTP. On success marks phone_verified=true, status ACTIVE, issues accessToken (15m) + refreshToken (7d, rotation).")
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest) {

        log.info("POST /auth/verify-otp phone={}", request.phone());
        var response = authService.verifyOtp(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Login with phone + password",
            description = "Brute-force protected: max 5 failed attempts per 15 min per phone → account locked 423. Returns JWT. Rate limited via Bucket4j.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        log.info("POST /auth/login phone={}", request.phone());
        var response = authService.login(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Refresh Access Token with rotation",
            description = "Uses opaque refresh token (SHA-256 hashed in DB). Validates not revoked/expired, rotates: old token revoked, new pair issued.")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {

        log.info("POST /auth/refresh");
        var response = authService.refresh(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout — revoke refresh token",
            description = "Revokes refresh token (idempotent). Access token short-lived 15m so no blacklist needed for MVP.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {

        log.info("POST /auth/logout");
        authService.logout(request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Forgot Password Init — send OTP",
            description = "Sends OTP for password reset if phone exists. Same 5min expiry, 3 attempts.")
    @PostMapping("/forgot-password/init")
    public ResponseEntity<RegisterInitResponse> forgotPasswordInit(
            @Valid @RequestBody ForgotPasswordInitRequest request) {

        log.info("POST /auth/forgot-password/init phone={}", request.phone());
        var response = authService.forgotPasswordInit(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Forgot Password Reset — verify OTP + new password",
            description = "Verifies OTP and resets password to newPassword (8+ chars, 1 upper, 1 lower, 1 digit). Revokes all refresh tokens to force re-login.")
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<Void> forgotPasswordReset(
            @Valid @RequestBody ForgotPasswordResetRequest request) {

        log.info("POST /auth/forgot-password/reset phone={}", request.phone());
        authService.forgotPasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Health check for auth module", description = "Public endpoint to check auth service up")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth module UP — SmartTrust v1");
    }
}
