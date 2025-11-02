package com.hhplus.be.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 409 Conflict - 비즈니스 규칙 위반 (재고 부족, 중복 등)
 */
public class BusinessException extends BaseException {
    public BusinessException(String message, String errorCode) {
        super(message, HttpStatus.CONFLICT, errorCode);
    }
}