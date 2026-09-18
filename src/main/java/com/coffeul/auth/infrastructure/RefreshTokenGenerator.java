package com.coffeul.auth.infrastructure;

import java.security.SecureRandom;
import java.util.Base64;

/** refresh 토큰 값 자체 — JWT가 아니라 256비트 무작위 문자열 (rules.py AUTH_SPEC). */
public final class RefreshTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokenGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "rt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
