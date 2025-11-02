package com.hhplus.be.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 402 Payment Required - 포인트 부족
 */
public class InsufficientBalanceException extends BaseException {
    public InsufficientBalanceException(String message) {
        super(message, HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_POINT");
    }
}