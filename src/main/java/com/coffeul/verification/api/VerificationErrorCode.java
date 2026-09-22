package com.coffeul.verification.api;

import com.coffeul.common.error.ErrorCode;

/**
 * 이메일 인증(SM-1 · SM-2) 실패 코드 — api.py 원본 그대로.
 * 에러 코드는 그대로 HTTP 응답에 나가는 공개 계약이고, 토큰을 소비하는 member가 EV008을 써야 해서 api에 둔다.
 */
public enum VerificationErrorCode implements ErrorCode {

    UNSUPPORTED_SCHOOL_DOMAIN(400, "EV001", "지원하지 않는 학교 이메일입니다."),
    RESEND_TOO_SOON(429, "EV002", "잠시 후 다시 요청해주세요."),
    DAILY_LIMIT_EXCEEDED(429, "EV003", "오늘은 더 이상 인증코드를 보낼 수 없어요."),
    CODE_MISMATCH(400, "EV004", "인증코드가 일치하지 않아요."),
    CODE_EXPIRED(410, "EV005", "인증코드가 만료됐어요. 다시 요청해주세요."),
    EMAIL_ALREADY_REGISTERED(409, "EV006", "이미 가입된 이메일이에요."),
    MAIL_DELIVERY_FAILED(503, "EV007", "메일을 보내지 못했어요. 잠시 후 다시 시도해주세요."),
    VERIFICATION_TOKEN_INVALID(400, "EV008", "이메일 인증을 다시 진행해주세요.");

    private final int status;
    private final String code;
    private final String message;

    VerificationErrorCode(int status, String code, String message) {
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
