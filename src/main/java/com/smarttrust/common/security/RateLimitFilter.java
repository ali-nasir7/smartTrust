package com.smarttrust.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrust.common.exception.ApiError;
import com.smarttrust.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        RateLimitService.EndpointType type = null;
        String key = getClientIp(request);

        // Match endpoints
        if (path.endsWith("/auth/login")) {
            type = RateLimitService.EndpointType.LOGIN;
        } else if (path.endsWith("/auth/register/init") || path.endsWith("/auth/register/resend-otp")) {
            type = RateLimitService.EndpointType.REGISTER; // covers unlimited-request prevention for OTP
        } else if (path.endsWith("/auth/verify-otp")) {
            type = RateLimitService.EndpointType.OTP_VERIFY;
        } else if (path.contains("/forgot-password")) {
            type = RateLimitService.EndpointType.FORGOT_PASSWORD;
        }

        if (type != null) {
            RateLimitService.RateLimitResult result = rateLimitService.tryConsume(key, type);
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(result.getRemaining()));

            if (!result.isConsumed()) {
                log.warn("Rate limit exceeded key={} type={} path={} retryAfter={}s", key, type, path, result.getRetryAfterSeconds());

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(result.getRetryAfterSeconds()));

                String traceId = MDC.get("correlationId");
                ApiError error = ApiError.of(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                        ErrorCode.RATE_LIMIT_EXCEEDED,
                        "Too many requests for " + type + ". Retry after " + result.getRetryAfterSeconds() + " seconds.",
                        path,
                        traceId
                );
                response.getWriter().write(objectMapper.writeValueAsString(error));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
