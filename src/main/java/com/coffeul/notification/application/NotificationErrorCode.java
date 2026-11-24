package com.coffeul.notification.application;

import com.coffeul.common.error.ErrorCode;

public enum NotificationErrorCode implements ErrorCode {

    INVALID_PUSH_TOKEN(400, "NT001", "푸시 토큰 형식이 올바르지 않아요.");

    private final int status;
    private final String code;
    private final String message;

    NotificationErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
