package com.coffeul.verification.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 인증코드 · 인증 토큰은 DB에 평문으로 두지 않고 이 해시만 저장한다 (REQ-EV-002 · 004).
 * <p>auth 모듈의 TokenHasher와 같은 계산이지만, 모듈 경계상 남의 infrastructure를 참조할 수 없어 따로 둔다.
 * 세 번째 모듈이 같은 게 필요해지면 common(OPEN 모듈)으로 올리는 게 맞다.
 */
public final class VerificationHasher {

    private VerificationHasher() {
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
