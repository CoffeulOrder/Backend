package com.coffeul.verification.api;

/**
 * 인증코드를 보내기 전에 "이 이메일이 이미 가입돼 있나"를 확인하는 역방향 포트 (rules.py AUTH_SPEC의 자격 조회 포트와 같은 방식).
 * <p>verification이 정의하고 member가 구현한다 — verification이 member를 import하면
 * member는 SM-3(가입)에서 verification.api를 써야 해서 순환 의존이 된다.
 */
public interface MemberAccountPort {

    /** 탈퇴하지 않은(= email 컬럼이 살아 있는) 회원이 이 이메일로 존재하는지. */
    boolean existsByEmail(String email);
}
