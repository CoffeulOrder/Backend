package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.order.api.OrderQueryApi;
import com.coffeul.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * order.api.OrderQueryApi 검증 — SM-7(탈퇴) U005 판정이 쓸 "활성(종료되지 않은) 주문 수"가
 * 종료 상태(COMPLETED · CANCELED · REJECTED · EXPIRED)를 정확히 제외하는지 실제 MySQL로 확인한다.
 */
class OrderQueryApiTest extends AbstractIntegrationTest {

    @Autowired
    private OrderQueryApi orderQueryApi;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestFixtures fixtures;
    private long memberId;
    private long storeId;
    private long schoolId;

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
    void 종료_상태가_아닌_주문만_활성_주문으로_센다() {
        fixtures.createOrder(memberId, storeId, schoolId, "PENDING_PAYMENT");
        fixtures.createOrder(memberId, storeId, schoolId, "REQUESTED");
        fixtures.createOrder(memberId, storeId, schoolId, "MAKING");
        fixtures.createOrder(memberId, storeId, schoolId, "COMPLETED");
        fixtures.createOrder(memberId, storeId, schoolId, "CANCELED");
        fixtures.createOrder(memberId, storeId, schoolId, "REJECTED");
        fixtures.createOrder(memberId, storeId, schoolId, "EXPIRED");

        assertThat(orderQueryApi.countActiveOrders(memberId)).isEqualTo(3);
    }

    @Test
    void 주문이_없으면_0을_반환한다() {
        assertThat(orderQueryApi.countActiveOrders(memberId)).isZero();
    }

    @Test
    void 다른_회원의_주문은_세지_않는다() {
        long otherMemberId = fixtures.createMember(schoolId, "other-" + UUID.randomUUID() + "@test.ac.kr", "ACTIVE");
        fixtures.createOrder(otherMemberId, storeId, schoolId, "REQUESTED");

        assertThat(orderQueryApi.countActiveOrders(memberId)).isZero();
    }
}
