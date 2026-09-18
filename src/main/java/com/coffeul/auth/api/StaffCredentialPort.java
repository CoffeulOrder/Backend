package com.coffeul.auth.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** auth가 store의 직원 로그인 자격을 조회하는 포트 — auth는 store를 직접 참조하지 않는다 (rules.py AUTH_SPEC). */
public interface StaffCredentialPort {

    Optional<StaffCredential> findByLoginId(String loginId);

    Optional<StaffCredential> findById(Long staffId);

    void recordLoginSuccess(Long staffId, Instant now);

    /** 실패 1회를 반영한다. lockedUntil이 null이 아니면 이번 실패로 30초 대기까지 같이 건다. */
    void recordLoginFailure(Long staffId, Instant now, Instant lockedUntil);

    /** MS-2 응답의 stores 목록 — OWNER는 merchant 소유 매장 전체, STAFF는 staff_store, ADMIN은 빈 목록. */
    List<StaffStoreSummary> findAccessibleStores(Long staffId);

    record StaffCredential(Long staffId, String role, Long merchantId, String name, String passwordHash,
                            boolean active, int failedLoginCount, Instant lockedUntil) {
    }

    record StaffStoreSummary(Long storeId, String name, String status) {
    }
}
