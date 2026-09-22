package com.coffeul.member.infrastructure;

import com.coffeul.member.application.PushTokenDeactivationPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * <b>임시 어댑터</b> — notification 모듈(태완 형)이 생기기 전까지만 쓴다.
 *
 * <p>일부러 JPA 엔티티로 매핑하지 않았다. 엔티티를 만들면 member가 device_token 테이블을 소유하는 모양이
 * 되고 나중에 옮길 때 지울 게 늘어난다 — `TemporarySchoolLookupAdapter`와 같은 이유다. 갱신 한 줄이라
 * JdbcTemplate로 충분하다.
 *
 * <p>이미 비활성인 행은 건드리지 않는다. 다시 실행해도 결과가 같아야 하고(탈퇴는 멱등해야 한다),
 * deactivated_at이 최초 비활성 시각을 유지해야 하기 때문이다.
 */
@Component
public class TemporaryPushTokenDeactivationAdapter implements PushTokenDeactivationPort {

    private final JdbcTemplate jdbcTemplate;

    public TemporaryPushTokenDeactivationAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int deactivateAllForMember(Long memberId) {
        return jdbcTemplate.update(
                "UPDATE `device_token` SET `is_active` = FALSE, `deactivated_at` = ? "
                        + "WHERE `owner_type` = 'MEMBER' AND `owner_id` = ? AND `is_active` = TRUE",
                java.sql.Timestamp.from(Instant.now()), memberId);
    }
}
