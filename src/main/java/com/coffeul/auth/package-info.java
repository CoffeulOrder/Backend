/**
 * 인증/인가. 로그인, 토큰 발급 · 재발급, refresh 토큰 회전(재사용 감지 시 계열 폐기).
 * 담당: 민섭. 다른 모듈을 import하지 않는다 — 자격 조회는 MemberCredentialPort · StaffCredentialPort로 역방향 정의(member · store가 구현).
 * 컨트롤러는 auth.api.AuthUser(id, type, role, schoolId, merchantId)만 받는다.
 */
package com.coffeul.auth;
