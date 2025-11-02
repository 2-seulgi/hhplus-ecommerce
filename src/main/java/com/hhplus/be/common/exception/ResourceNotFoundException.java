package com.hhplus.be.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 404 Not Found - 리소스 없음
 */
public class ResourceNotFoundException extends BaseException {
    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}