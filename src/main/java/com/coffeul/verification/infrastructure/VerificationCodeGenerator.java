package com.coffeul.verification.infrastructure;

import java.security.SecureRandom;

/** 6자리 숫자 인증코드 (REQ-EV-002). 앞자리 0도 살려야 해서 문자열로 만든다. */
public final class VerificationCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private VerificationCodeGenerator() {
    }

    public static String generate() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
