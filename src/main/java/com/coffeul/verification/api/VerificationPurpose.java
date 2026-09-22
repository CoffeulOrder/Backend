package com.coffeul.verification.api;

import java.util.Optional;

/**
 * 인증코드 · 인증 토큰의 용도. 용도가 다르면 절대 섞이지 않는다 (REQ-EV-005).
 * member가 SM-3 · SM-6에서 토큰을 소비할 때 같이 넘기므로 api에 둔다.
 */
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
