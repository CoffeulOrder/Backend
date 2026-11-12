package com.coffeul.notification.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * `device_token` 쓰기. 토큰(uk_device_token_token) 기준 upsert 한 문장으로 처리한다 — 로그인 직후 등록과
 * 매장 변경 재등록이 동시에 들어와도 유니크 위반 없이 한 행으로 수렴해야 하기 때문이다.
 * 갱신 쪽은 주인 · 앱 종류 · 매장을 통째로 덮어써서 ck_device_token_app_owner를 항상 만족한다.
 */
@Repository
public class DeviceTokenJdbcRepository {

    private static final String UPSERT = "INSERT INTO `device_token` (`owner_type`, `owner_id`, `app_type`, "
            + "`store_id`, `expo_push_token`, `platform`, `is_active`, `last_registered_at`) "
            + "VALUES (?, ?, ?, ?, ?, ?, TRUE, ?) "
            + "ON DUPLICATE KEY UPDATE `owner_type` = ?, `owner_id` = ?, `app_type` = ?, `store_id` = ?, "
            + "`platform` = ?, `is_active` = TRUE, `deactivated_at` = NULL, `last_registered_at` = ?";

    private final JdbcTemplate jdbcTemplate;

    public DeviceTokenJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return 등록(또는 갱신)된 행의 id */
    public Long upsert(String ownerType, Long ownerId, String appType, Long storeId,
                       String expoPushToken, String platform, Instant now) {
        jdbcTemplate.update(UPSERT,
                ownerType, ownerId, appType, storeId, expoPushToken, platform, now,
                ownerType, ownerId, appType, storeId, platform, now);
        return jdbcTemplate.queryForObject(
                "SELECT `id` FROM `device_token` WHERE `expo_push_token` = ?", Long.class, expoPushToken);
    }
}
