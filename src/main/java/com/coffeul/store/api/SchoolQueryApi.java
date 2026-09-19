package com.coffeul.store.api;

import java.util.Optional;

/** 다른 모듈이 학교명 · 캠퍼스를 조회할 때 쓰는 포트 (예: SM-3 · SM-4 응답의 school.name · campus). */
public interface SchoolQueryApi {

    Optional<SchoolView> findById(Long schoolId);
}
