package com.coffeul.auth.api;

import java.time.Instant;
import java.util.Optional;

/** auth가 member의 로그인 자격을 조회하는 포트 — auth는 member를 직접 참조하지 않는다 (rules.py AUTH_SPEC). */
public interface MemberCredentialPort {

    Optional<MemberCredential> findByEmail(String email);

    Optional<MemberCredential> findById(Long memberId);

    /** 로그인·갱신·명시적 폐기는 같은 회원 행을 먼저 잠가 순서를 보장한다. */
    Optional<MemberCredential> findByEmailForUpdate(String email);

    Optional<MemberCredential> findByIdForUpdate(Long memberId);

    void recordLoginSuccess(Long memberId, Instant now);

    /** 실패 1회를 반영한다. lockedUntil이 null이 아니면 이번 실패로 잠금까지 같이 건다. */
    void recordLoginFailure(Long memberId, Instant now, Instant lockedUntil);

    record MemberCredential(Long memberId, Long schoolId, String name, String passwordHash, boolean active,
                             int failedLoginCount, Instant lockedUntil) {
    }
}
