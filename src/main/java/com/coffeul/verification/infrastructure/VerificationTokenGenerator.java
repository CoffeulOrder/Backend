package com.coffeul.verification.infrastructure;

import java.security.SecureRandom;
import java.util.Base64;

/** 인증 토큰(evt_...) — JWT가 아니라 256비트 무작위 문자열이고, DB엔 SHA-256만 남는다 (REQ-EV-004). */
public final class VerificationTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private VerificationTokenGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "evt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
