package com.coffeul.verification.domain;

import java.util.Optional;

/** 인증코드 · 인증 토큰의 용도. 용도가 다르면 절대 섞이지 않는다 (REQ-EV-005). */
public enum VerificationPurpose {

    SIGNUP,
    PASSWORD_RESET;

    public static Optional<VerificationPurpose> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (VerificationPurpose purpose : values()) {
            if (purpose.name().equals(raw)) {
                return Optional.of(purpose);
            }
        }
        return Optional.empty();
    }
}
