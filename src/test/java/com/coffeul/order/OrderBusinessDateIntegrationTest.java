package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.support.TestFixtures;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** UTC 날짜가 바뀌기 전에도 영업일은 KST 자정을 기준으로 저장되어야 한다. */
class OrderBusinessDateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @CsvSource({
            "2026-11-09T14:59:59Z, 2026-11-09",
            "2026-11-09T15:00:00Z, 2026-11-10"
    })
    void orderBusinessDateUsesSeoulMidnight(String timestamp, LocalDate expectedDate) {
        Instant instant = Instant.parse(timestamp);
        TestFixtures fixtures = new TestFixtures(jdbcTemplate,
                Clock.fixed(instant, ZoneId.of("Asia/Seoul")));
        String unique = UUID.randomUUID().toString();
        long schoolId = fixtures.createSchool("날짜검증-" + unique);
        long merchantId = fixtures.createMerchant(
                String.format("%010d", unique.hashCode() & 0x7fffffff), new BigDecimal("0.0300"));
        long storeId = fixtures.createStore(merchantId, schoolId, "날짜검증매장", "OPEN");
        long memberId = fixtures.createMember(schoolId, unique + "@test.ac.kr", "ACTIVE");

        long placedOrderId = fixtures.createOrder(memberId, storeId, schoolId, "REQUESTED");
        long pendingOrderId = fixtures.createOrder(memberId, storeId, schoolId, "PENDING_PAYMENT");
        assertThat(businessDate(pendingOrderId)).isNull();
        fixtures.promoteOrderStatus(pendingOrderId, "REQUESTED");

        assertThat(businessDate(placedOrderId)).isEqualTo(expectedDate);
        assertThat(businessDate(pendingOrderId)).isEqualTo(expectedDate);
    }

    private LocalDate businessDate(long orderId) {
        return jdbcTemplate.queryForObject("SELECT business_date FROM orders WHERE id = ?",
                LocalDate.class, orderId);
    }
}
