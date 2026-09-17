/**
 * 학교 · 사장님 · 매장 · 직원 계정, 영업 상태(개시/브레이크/마감), 매장 접근 권한 판정.
 * 담당: 민섭. store.api.StoreAccessPolicy.check(AuthUser, storeId)를 다른 모든 매장용 모듈이 사용한다
 * (ADMIN 통과 · OWNER는 merchant 일치 · STAFF는 staff_store + 계정 ACTIVE).
 */
package com.coffeul.store;
