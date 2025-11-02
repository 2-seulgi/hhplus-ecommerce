package com.hhplus.be.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 400 Bad Request - 잘못된 요청
 */
public class InvalidInputException extends BaseException {
    public InvalidInputException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_INPUT");
    }
}