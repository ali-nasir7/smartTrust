package com.smarttrust.modules.auth.domain.service;

import com.smarttrust.common.exception.BusinessException;
import com.smarttrust.common.exception.ErrorCode;
import com.smarttrust.modules.auth.domain.entity.RefreshToken;
import com.smarttrust.modules.auth.infrastructure.persistence.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${smarttrust.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @Value("${smarttrust.security.refresh-token-length-bytes:48}")
    private int tokenLengthBytes;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates raw refresh token (opaque) + saves its SHA-256 hash.
     * Returns raw token to be sent to client (once).
     */
    @Transactional
    public RawRefreshTokenResult createRefreshToken(Long userId, String ip, String deviceInfo) {
        byte[] randomBytes = new byte[tokenLengthBytes];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String hash = sha256(rawToken);

        RefreshToken entity = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash)
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .createdIp(ip)
                .deviceInfo(deviceInfo)
                .build();

        RefreshToken saved = refreshTokenRepository.save(entity);

        log.info("Refresh token created userId={} id={} expiresAt={}", userId, saved.getId(), saved.getExpiresAt());

        return new RawRefreshTokenResult(rawToken, saved);
    }

    @Transactional(readOnly = true)
    public RefreshToken findByRawTokenOrThrow(String rawToken) {
        String hash = sha256(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> BusinessException.of(ErrorCode.AUTH_REFRESH_TOKEN_INVALID, HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (token.isRevoked()) {
            throw BusinessException.of(ErrorCode.AUTH_REFRESH_TOKEN_REVOKED, HttpStatus.UNAUTHORIZED, "Refresh token revoked. Please login again.");
        }
        if (token.isExpired()) {
            throw BusinessException.of(ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED, "Refresh token expired. Please login again.");
        }
        return token;
    }

    @Transactional
    public void revokeToken(RefreshToken token) {
        token.revoke();
        refreshTokenRepository.save(token);
        log.info("Refresh token revoked id={} userId={}", token.getId(), token.getUserId());
    }

    @Transactional
    public void revokeAllByUserId(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("All refresh tokens revoked for userId={}", userId);
    }

    /**
     * Rotation: revoke old, create new. Returns new raw token + entity.
     */
    @Transactional
    public RawRefreshTokenResult rotateRefreshToken(RefreshToken oldToken, String ip, String deviceInfo) {
        // Revoke old
        oldToken.revoke();
        refreshTokenRepository.save(oldToken);

        // Create new
        return createRefreshToken(oldToken.getUserId(), ip, deviceInfo);
    }

    public static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash refresh token", e);
        }
    }

    public record RawRefreshTokenResult(String rawToken, RefreshToken entity) {}
}
