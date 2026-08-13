package com.smarttrust.common.security;

import com.smarttrust.common.exception.BusinessException;
import com.smarttrust.common.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${smarttrust.jwt.secret}")
    private String jwtSecret;

    @Value("${smarttrust.jwt.access-expiration-ms}")
    private long accessExpirationMs;

    @Value("${smarttrust.jwt.issuer}")
    private String issuer;

    private SecretKey key;

    @PostConstruct
    public void init() {
        // Secret must be base64 or plain at least 64 chars for HS256. We support plain string if not base64.
        try {
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
            if (keyBytes.length < 32) throw new IllegalArgumentException("short");
            this.key = Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            // Treat as plain UTF-8 string, ensure length >=64
            if (jwtSecret.length() < 32) {
                log.warn("JWT secret too short, padding. Please provide 64+ chars in env.");
            }
            this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        log.info("JwtTokenProvider initialized issuer={} accessExpMs={}", issuer, accessExpirationMs);
    }

    public String generateAccessToken(Long userId, String phone, String role, String status) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessExpirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("phone", phone)
                .claim("role", role)
                .claim("status", status)
                .signWith(key)
                .compact();
    }

    public Claims validateAndParse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw BusinessException.of(ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED, org.springframework.http.HttpStatus.UNAUTHORIZED, "JWT expired");
        } catch (JwtException ex) {
            throw BusinessException.of(ErrorCode.AUTH_REFRESH_TOKEN_INVALID, org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid JWT: " + ex.getMessage());
        }
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = validateAndParse(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getPhoneFromToken(String token) {
        return validateAndParse(token).get("phone", String.class);
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }
}
