package com.coffeul.auth.api;

/**
 * 비밀번호가 바뀌면 그 회원의 살아 있는 refresh 토큰을 전부 폐기한다 (REQ-U-005 · 006).
 * <p>비밀번호를 바꾼 이유가 "남이 내 계정을 쓰고 있다"인 경우가 많아서, 바꾸기만 하고 기존 세션을 두면
 * 침입자가 그대로 로그인 상태로 남는다. access 토큰은 최대 1시간 더 살아 있지만 그건 계정 상태 확인으로 막는다.
 * <p>refresh 토큰을 다루는 코드는 auth 뒤에만 있다 — 다른 모듈이 refresh_token 테이블을 직접 건드리지 않는다.
 */
public interface RefreshTokenRevoker {

    /** @return 폐기한 토큰 수 (이미 폐기됐거나 없으면 0 — 멱등) */
    int revokeAllForMember(Long memberId);
}
