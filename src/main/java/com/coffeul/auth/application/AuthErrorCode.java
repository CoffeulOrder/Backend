package com.coffeul.auth.application;

import com.coffeul.common.error.ErrorCode;

/** 인증(MS-1~4) 실패 코드 — api.py 원본 그대로. */
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(401, "AUTH_001", "이메일 또는 비밀번호가 올바르지 않습니다."),
    LOCKED(423, "AUTH_002", "로그인 시도가 많아 잠시 잠겼어요."),
    REFRESH_INVALID(401, "AUTH_003", "다시 로그인해주세요."),
    ACCOUNT_UNUSABLE(403, "AUTH_005", "이용할 수 없는 계정이에요.");

    private final int status;
    private final String code;
    private final String message;

    AuthErrorCode(int status, String code, String message) {
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
