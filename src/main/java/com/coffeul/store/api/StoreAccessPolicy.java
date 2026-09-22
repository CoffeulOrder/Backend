package com.coffeul.store.api;

import com.coffeul.auth.api.AuthUser;

/**
 * 매장 접근 권한 판정 (rules.py AUTH_SPEC: "매장 권한").
 * ADMIN 통과 · OWNER는 merchant 일치 · STAFF는 staff_store + 계정 ACTIVE. 주문 상태 전이(MS-22~26)를
 * 비롯해 매장을 다루는 모든 모듈이 이 포트 하나로 판정한다 — 판정 로직을 두 곳에 두지 않는다.
 */
public interface StoreAccessPolicy {

    /**
     * 권한이 없으면 {@link StoreErrorCode#FORBIDDEN}(403 AUTH_004)를 던진다.
     * 매장 자체가 없으면 {@link StoreErrorCode#NOT_FOUND}(404 ST001).
     */
    void check(AuthUser authUser, Long storeId);
}
