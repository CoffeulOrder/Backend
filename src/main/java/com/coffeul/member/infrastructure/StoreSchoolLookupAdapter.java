package com.coffeul.member.infrastructure;

import com.coffeul.member.application.SchoolLookupPort;
import com.coffeul.store.api.SchoolQueryApi;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 학교 조회를 store 모듈에 위임한다.
 *
 * <p>2026-09-19 이전에는 `school` 테이블의 주인 모듈이 없어서 JdbcTemplate로 직접 읽는 임시 어댑터였다
 * (`TemporarySchoolLookupAdapter`). 민섭이 `store.api.SchoolQueryApi`를 열면서 예고한 대로 이 클래스만
 * 바꿨고, `SchoolLookupPort`와 그걸 쓰는 서비스 · 응답은 손대지 않았다.
 */
@Component
public class StoreSchoolLookupAdapter implements SchoolLookupPort {

    private final SchoolQueryApi schoolQueryApi;

    public StoreSchoolLookupAdapter(SchoolQueryApi schoolQueryApi) {
        this.schoolQueryApi = schoolQueryApi;
    }

    @Override
    public Optional<SchoolInfo> findById(Long schoolId) {
        return schoolQueryApi.findById(schoolId)
                .map(school -> new SchoolInfo(school.id(), school.name(), school.campus()));
    }
}
