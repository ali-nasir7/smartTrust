package com.smarttrust.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        ErrorCode errorCode,
        String message,
        String path,
        String traceId,
        Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String error, ErrorCode code, String msg, String path, String traceId) {
        return ApiError.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(error)
                .errorCode(code)
                .message(msg)
                .path(path)
                .traceId(traceId)
                .build();
    }
}
