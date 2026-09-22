package com.coffeul.member.domain;

import java.nio.charset.StandardCharsets;

/**
 * 비밀번호 규칙 (REQ-U-002): 8자 이상 72바이트 이하, 영문과 숫자를 모두 포함.
 * <p>72바이트 상한은 BCrypt가 그 뒤를 조용히 잘라버리기 때문이다 — 넘겨서 저장하면
 * 사용자가 입력한 것과 실제 검사되는 것이 달라진다.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
                hasLetter = true;
            } else if (c >= '0' && c <= '9') {
                hasDigit = true;
            }
        }
        return hasLetter && hasDigit;
    }
}
