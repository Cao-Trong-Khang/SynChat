package com.synchat.backend.common.exception;

public enum ErrorCode {
    UNAUTHORIZED,
    FORBIDDEN,
    VALIDATION_ERROR,
    EMAIL_ALREADY_EXISTS,
    INVALID_CREDENTIALS,
    NOT_FOUND,
    EVENT_CURSOR_EXPIRED,
    MESSAGE_TOO_LARGE,
    RATE_LIMITED,
    INTERNAL_ERROR
}
