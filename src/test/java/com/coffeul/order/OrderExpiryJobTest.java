package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.order.application.OrderExpiryJob;
import com.coffeul.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 대기 만료 작업 검증 (rules.py JOBS: PENDING_PAYMENT · expires_at 지남 → EXPIRED + 이력).
 * MS-27이 아직 없어 "UNKNOWN 시도 없음 · 마지막 READY 후 10분" 조건은 다루지 않는다 (OrderExpiryJob 참고).
 */
class OrderExpiryJobTest extends AbstractIntegrationTest {

    @Autowired
    private OrderExpiryJob orderExpiryJob;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestFixtures fixtures;
    private long schoolId;
    private long storeId;
    private long memberId;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        schoolId = fixtures.createSchool("테스트대학교-" + unique);
        long merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & 0x7fffffff),
                new BigDecimal("0.0300"));
        storeId = fixtures.createStore(merchantId, schoolId, "테스트매장", "OPEN");
        memberId = fixtures.createMember(schoolId, "member-" + unique + "@test.ac.kr", "ACTIVE");
    }

    @Test
    void 결제_대기_시간이_지난_주문을_만료시키고_이력을_남긴다() {
        long orderId = fixtures.createOrder(memberId, storeId, schoolId, "PENDING_PAYMENT");
        fixtures.ageOrderExpiresAt(orderId, Instant.now().minusSeconds(60));

        int expired = orderExpiryJob.expireOverdue(Instant.now());

        assertThat(expired).isEqualTo(1);
        Map<String, Object> orderRow = jdbcTemplate.queryForMap("SELECT * FROM `orders` WHERE `id` = ?", orderId);
        assertThat(orderRow.get("status")).isEqualTo("EXPIRED");
        assertThat(orderRow.get("expired_at")).isNotNull();

        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT * FROM `order_status_history` WHERE `order_id` = ?", orderId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("from_status")).isEqualTo("PENDING_PAYMENT");
        assertThat(history.get(0).get("to_status")).isEqualTo("EXPIRED");
        assertThat(history.get(0).get("actor_type")).isEqualTo("SYSTEM");
        assertThat(history.get(0).get("actor_id")).isNull();
    }

    @Test
    void 아직_만료_시간이_안된_주문은_건드리지_않는다() {
        long orderId = fixtures.createOrder(memberId, storeId, schoolId, "PENDING_PAYMENT");

        int expired = orderExpiryJob.expireOverdue(Instant.now());

        assertThat(expired).isEqualTo(0);
        String status = jdbcTemplate.queryForObject("SELECT `status` FROM `orders` WHERE `id` = ?", String.class, orderId);
        assertThat(status).isEqualTo("PENDING_PAYMENT");
    }
}
