package com.hhplus.be.common.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ErrorResponse {
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String code;
    private final String message;
    private final String path;

    private ErrorResponse(int status, String error, String code, String message, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.code = code;
        this.message = message;
        this.path = path;
    }

    public static ErrorResponse of(int status, String error, String code, String message, String path) {
        return new ErrorResponse(status, error, code, message, path);
    }
}