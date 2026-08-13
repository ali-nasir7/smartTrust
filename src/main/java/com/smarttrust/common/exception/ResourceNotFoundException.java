package com.smarttrust.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }
    public ResourceNotFoundException(ErrorCode code, String message) {
        super(code, HttpStatus.NOT_FOUND, message);
    }
}
