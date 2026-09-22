package com.coffeul.member.application;

import com.coffeul.common.error.ErrorCode;

/** 회원(SM-3~7) 실패 코드 — api.py 원본 그대로. */
public enum MemberErrorCode implements ErrorCode {

    EMAIL_ALREADY_REGISTERED(409, "U001", "이미 가입된 이메일이에요."),
    PASSWORD_POLICY_VIOLATION(400, "U002", "비밀번호는 8자 이상, 영문과 숫자를 포함해야 해요."),
    REQUIRED_TERMS_NOT_AGREED(400, "U003", "필수 약관에 동의해주세요."),
    PASSWORD_MISMATCH(401, "U004", "현재 비밀번호가 올바르지 않아요."),
    ACTIVE_ORDER_EXISTS(409, "U005", "진행 중인 주문이 있어 탈퇴할 수 없어요.");

    private final int status;
    private final String code;
    private final String message;

    MemberErrorCode(int status, String code, String message) {
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
