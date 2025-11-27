package com.hhplus.be.common.exception;

import org.springframework.http.HttpStatus;

public class LockAcquisitionException extends BaseException {

    public LockAcquisitionException(String message) {
        super(message, HttpStatus.LOCKED, "LOCK_ACQUISITION_FAILED");
    }
}
