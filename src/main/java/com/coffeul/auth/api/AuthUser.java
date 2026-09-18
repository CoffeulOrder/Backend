package com.coffeul.auth.api;

/**
 * 컨트롤러가 인증 주체로 받는 유일한 타입 (rules.py AUTH_SPEC).
 * 토큰 파싱 코드는 각 모듈에 두지 않고, 이 타입만 주고받는다.
 */
public record AuthUser(Long id, SubjectType type, Role role, Long schoolId, Long merchantId) {

    public enum SubjectType {
        MEMBER, STAFF
    }

    public enum Role {
        CUSTOMER, STAFF, OWNER, ADMIN
    }
}
