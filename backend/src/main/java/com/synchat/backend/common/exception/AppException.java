package com.synchat.backend.common.exception;

public class AppException extends RuntimeException {

    private final ErrorCode code;

    public AppException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
