package com.coffeul.member.application;

import java.util.Optional;

/**
 * 가입 · 내 정보 응답에 들어가는 학교 이름 · 캠퍼스를 가져오는 포트.
 * <p><b>임시</b>: `school` 테이블의 주인 모듈이 아직 정해지지 않았다(학교 · 사장님 · 매장 뼈대는 민섭 담당).
 * 정해지면 이 포트의 어댑터만 store.api(또는 school 모듈) 호출로 바꾸면 되고,
 * 서비스와 응답 형태는 그대로 둔다.
 */
public interface SchoolLookupPort {

    Optional<SchoolInfo> findById(Long schoolId);

    record SchoolInfo(Long schoolId, String name, String campus) {
    }
}
