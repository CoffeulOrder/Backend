package com.coffeul.member.infrastructure;

import com.coffeul.member.application.SchoolLookupPort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * <b>임시 어댑터</b> — `school` 테이블의 주인 모듈이 정해지기 전까지만 쓴다.
 * <p>일부러 JPA 엔티티로 매핑하지 않았다. 엔티티를 만들면 member가 school 테이블을 소유하는 모양이 되고,
 * 나중에 옮길 때 지워야 할 게 늘어난다. 읽기 한 줄짜리 조회라 JdbcTemplate로 충분하다.
 * 민섭이 store.api(또는 school 모듈)에 조회 API를 열면 이 클래스만 그쪽 호출로 바꾼다.
 */
@Component
public class TemporarySchoolLookupAdapter implements SchoolLookupPort {

    private final JdbcTemplate jdbcTemplate;

    public TemporarySchoolLookupAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<SchoolInfo> findById(Long schoolId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT `id`, `name`, `campus` FROM `school` WHERE `id` = ?",
                    (rs, rowNum) -> new SchoolInfo(rs.getLong("id"), rs.getString("name"), rs.getString("campus")),
                    schoolId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
