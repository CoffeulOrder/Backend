package com.coffeul.auth.api;

/**
 * 로그인 · 가입 완료(member) · 재발급에서만 호출하는 토큰 발급기 (rules.py AUTH_SPEC).
 * 토큰을 만드는 코드는 이 뒤에만 있다 — 다른 곳에서 JWT를 직접 만들지 않는다.
 */
public interface TokenIssuer {

    /** 로그인 · 가입처럼 새 refresh 계열을 시작할 때. */
    TokenPair issue(AuthUser user);

    /** 재발급(MS-3)처럼 기존 refresh 계열을 이어갈 때. */
    TokenPair rotate(AuthUser user, String familyId);

    record TokenPair(String accessToken, String refreshToken, long expiresIn) {
    }
}
