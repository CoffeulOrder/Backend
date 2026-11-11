package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MS-17(내 주문 목록) · MS-18(주문 상세 · 대기 건수) 엔드투엔드 검증.
 * 주문은 MS-16 API가 아니라 픽스처로 직접 만든다 — MS-16의 인증 방식이 바뀌어도 이 테스트는 영향받지 않는다.
 */
class OrderQueryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    private TestFixtures fixtures;
    private long schoolId;
    private long merchantId;
    private long storeId;
    private long otherStoreId;
    private long memberId;
    private long otherMemberId;
    private long menuItemId;
    private long optionItemId;
    private String customerAuth;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        schoolId = fixtures.createSchool("테스트대학교-" + unique);
        merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & 0x7fffffff),
                new BigDecimal("0.0300"));
        storeId = fixtures.createStore(merchantId, schoolId, "범석관점", "OPEN");
        otherStoreId = fixtures.createStore(merchantId, schoolId, "뉴밀레니엄관점", "OPEN");
        memberId = fixtures.createMember(schoolId, "member-" + unique + "@test.ac.kr", "ACTIVE");
        otherMemberId = fixtures.createMember(schoolId, "other-" + unique + "@test.ac.kr", "ACTIVE");
        menuItemId = fixtures.createMenuItem(storeId, "아메리카노", 2000, false);
        long groupId = fixtures.createOptionGroup(menuItemId, "온도", "TEMPERATURE", true, 1, 1);
        optionItemId = fixtures.createOptionItem(groupId, "ICE", 0, true);
        customerAuth = customerToken(memberId);
    }

    private String customerToken(long id) {
        return "Bearer " + jwtAccessTokenService.encode(
                new AuthUser(id, AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, schoolId, null), Instant.now());
    }

    private String staffToken() {
        long staffId = fixtures.createStaffAccount(merchantId, "STAFF", "staff-" + UUID.randomUUID().toString().substring(0, 8),
                "pw1234!!", "테스트직원", "ACTIVE");
        return "Bearer " + jwtAccessTokenService.encode(
                new AuthUser(staffId, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, merchantId), Instant.now());
    }

    private long orderWithLine(long member, long store, String status, String menuName) {
        long orderId = fixtures.createOrder(member, store, schoolId, status);
        fixtures.createOrderLine(orderId, menuItemId, menuName, 2000, 2);
        return orderId;
    }

    // ------------------------------------------------------------------ MS-17

    @Test
    void 내_주문목록은_최신순이고_주문_요약과_매장명이_담긴다() throws Exception {
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long older = orderWithLine(memberId, storeId, "COMPLETED", "아메리카노");
        long newer = fixtures.createOrder(memberId, otherStoreId, schoolId, "READY");
        fixtures.createOrderLine(newer, menuItemId, "카페라떼", 2500, 1);
        fixtures.createOrderLine(newer, menuItemId, "아메리카노", 2000, 1);
        fixtures.setOrderTimes(older, base.minusSeconds(60), base.minusSeconds(60));
        fixtures.setOrderTimes(newer, base, base);

        mockMvc.perform(get("/api/v1/orders").header("Authorization", customerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].orderId").value(newer))
                .andExpect(jsonPath("$.data.content[0].storeName").value("뉴밀레니엄관점"))
                .andExpect(jsonPath("$.data.content[0].status").value("READY"))
                .andExpect(jsonPath("$.data.content[0].itemSummary").value("카페라떼 외 1건"))
                .andExpect(jsonPath("$.data.content[0].totalAmount").value(4000))
                .andExpect(jsonPath("$.data.content[0].placedAt", endsWith("+09:00")))
                .andExpect(jsonPath("$.data.content[1].orderId").value(older))
                .andExpect(jsonPath("$.data.content[1].itemSummary").value("아메리카노"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 만료된_결제대기는_빼고_만료_전_결제대기는_목록에_남는다() throws Exception {
        long pending = orderWithLine(memberId, storeId, "PENDING_PAYMENT", "아메리카노");
        orderWithLine(memberId, storeId, "EXPIRED", "아메리카노");

        mockMvc.perform(get("/api/v1/orders").header("Authorization", customerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].orderId").value(pending))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.content[0].pickupNo").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].placedAt").value(nullValue()));
    }

    @Test
    void 다른_회원의_주문은_목록에_안_나온다() throws Exception {
        orderWithLine(otherMemberId, storeId, "REQUESTED", "아메리카노");
        long mine = orderWithLine(memberId, storeId, "REQUESTED", "아메리카노");

        mockMvc.perform(get("/api/v1/orders").header("Authorization", customerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].orderId").value(mine));
    }

    @Test
    void 페이지를_나눠_받을_수_있고_다음_페이지가_있으면_hasNext가_true다() throws Exception {
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long first = orderWithLine(memberId, storeId, "COMPLETED", "아메리카노");
        long second = orderWithLine(memberId, storeId, "COMPLETED", "아메리카노");
        long third = orderWithLine(memberId, storeId, "COMPLETED", "아메리카노");
        fixtures.setOrderTimes(first, base.minusSeconds(120), base.minusSeconds(120));
        fixtures.setOrderTimes(second, base.minusSeconds(60), base.minusSeconds(60));
        fixtures.setOrderTimes(third, base, base);

        mockMvc.perform(get("/api/v1/orders?page=0&size=2").header("Authorization", customerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].orderId").value(third))
                .andExpect(jsonPath("$.data.content[1].orderId").value(second))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mockMvc.perform(get("/api/v1/orders?page=1&size=2").header("Authorization", customerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].orderId").value(first))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 잘못된_페이지_값은_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/orders?size=0").header("Authorization", customerAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        mockMvc.perform(get("/api/v1/orders?size=51").header("Authorization", customerAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        mockMvc.perform(get("/api/v1/orders?page=-1").header("Authorization", customerAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    void 토큰이_없으면_401_직원_토큰이면_403이다() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("C002"));
        mockMvc.perform(get("/api/v1/orders").header("Authorization", staffToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    // ------------------------------------------------------------------ MS-18

    @Test
    void 주문_상세에_항목_옵션_금액_타임라인이_담긴다() throws Exception {
        long orderId = fixtures.createOrder(memberId, storeId, schoolId, "REQUESTED");
        long lineId = fixtures.createOrderLine(orderId, menuItemId, "아메리카노", 2000, 2);
        fixtures.createOrderLineOption(lineId, optionItemId, "온도", "ICE");

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId).header("Authorization", customerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.store.storeId").value(storeId))
                .andExpect(jsonPath("$.data.store.name").value("범석관점"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].menuName").value("아메리카노"))
                .andExpect(jsonPath("$.data.items[0].options[0]").value("ICE"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(2000))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].lineAmount").value(4000))
                .andExpect(jsonPath("$.data.subtotalAmount").value(4000))
                .andExpect(jsonPath("$.data.totalAmount").value(4000))
                .andExpect(jsonPath("$.data.rejectReason").value(nullValue()))
                .andExpect(jsonPath("$.data.timeline.placedAt", endsWith("+09:00")))
                .andExpect(jsonPath("$.data.timeline.acceptedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.timeline.canceledAt").value(nullValue()));
    }

    @Test
    void aheadCount는_같은_매장에서_나보다_먼저_접수된_대기_주문만_센다() throws Exception {
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long ahead1 = orderWithLine(otherMemberId, storeId, "REQUESTED", "아메리카노");
        long ahead2 = orderWithLine(otherMemberId, storeId, "ACCEPTED", "아메리카노");
        long ahead3 = orderWithLine(otherMemberId, storeId, "MAKING", "아메리카노");
        long readyAhead = orderWithLine(otherMemberId, storeId, "READY", "아메리카노");
        long completedAhead = orderWithLine(otherMemberId, storeId, "COMPLETED", "아메리카노");
        long otherStoreAhead = orderWithLine(otherMemberId, otherStoreId, "REQUESTED", "아메리카노");
        long mine = orderWithLine(memberId, storeId, "REQUESTED", "아메리카노");
        long behind = orderWithLine(otherMemberId, storeId, "REQUESTED", "아메리카노");
        for (long id : new long[]{ahead1, ahead2, ahead3, readyAhead, completedAhead, otherStoreAhead}) {
            fixtures.setOrderTimes(id, base.minusSeconds(100), base.minusSeconds(100));
        }
        fixtures.setOrderTimes(mine, base, base);
        fixtures.setOrderTimes(behind, base.plusSeconds(10), base.plusSeconds(10));

        mockMvc.perform(get("/api/v1/orders/{orderId}", mine).header("Authorization", customerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aheadCount").value(3));
    }

    @Test
    void cancelable은_REQUESTED일_때만_true고_대기_건수는_그_외_상태에서_null이다() throws Exception {
        long requested = orderWithLine(memberId, storeId, "REQUESTED", "아메리카노");
        long accepted = orderWithLine(memberId, storeId, "ACCEPTED", "아메리카노");
        long ready = orderWithLine(memberId, storeId, "READY", "아메리카노");

        mockMvc.perform(get("/api/v1/orders/{orderId}", requested).header("Authorization", customerAuth))
                .andExpect(jsonPath("$.data.cancelable").value(true));
        mockMvc.perform(get("/api/v1/orders/{orderId}", accepted).header("Authorization", customerAuth))
                .andExpect(jsonPath("$.data.cancelable").value(false))
                .andExpect(jsonPath("$.data.aheadCount").isNumber());
        mockMvc.perform(get("/api/v1/orders/{orderId}", ready).header("Authorization", customerAuth))
                .andExpect(jsonPath("$.data.cancelable").value(false))
                .andExpect(jsonPath("$.data.aheadCount").value(nullValue()));
    }

    @Test
    void 결제대기_주문은_접수_전이라_대기_건수와_픽업번호가_null이다() throws Exception {
        long pending = orderWithLine(memberId, storeId, "PENDING_PAYMENT", "아메리카노");

        mockMvc.perform(get("/api/v1/orders/{orderId}", pending).header("Authorization", customerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.pickupNo").value(nullValue()))
                .andExpect(jsonPath("$.data.aheadCount").value(nullValue()))
                .andExpect(jsonPath("$.data.cancelable").value(false))
                .andExpect(jsonPath("$.data.timeline.placedAt").value(nullValue()));
    }

    @Test
    void 거절된_주문은_거절_사유와_거절_시각이_나온다() throws Exception {
        long rejected = orderWithLine(memberId, storeId, "REJECTED", "아메리카노");
        fixtures.setOrderEventTime(rejected, "rejected_at", Instant.now());

        mockMvc.perform(get("/api/v1/orders/{orderId}", rejected).header("Authorization", customerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("OTHER"))
                .andExpect(jsonPath("$.data.timeline.rejectedAt", endsWith("+09:00")));
    }

    @Test
    void 취소된_주문은_취소_시각이_나온다() throws Exception {
        long canceled = orderWithLine(memberId, storeId, "CANCELED", "아메리카노");
        fixtures.setOrderEventTime(canceled, "canceled_at", Instant.now());

        mockMvc.perform(get("/api/v1/orders/{orderId}", canceled).header("Authorization", customerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.timeline.canceledAt", endsWith("+09:00")));
    }

    @Test
    void 남의_주문과_없는_주문은_구분없이_404_OD005다() throws Exception {
        long othersOrder = orderWithLine(otherMemberId, storeId, "REQUESTED", "아메리카노");

        mockMvc.perform(get("/api/v1/orders/{orderId}", othersOrder).header("Authorization", customerAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OD005"));
        mockMvc.perform(get("/api/v1/orders/{orderId}", 999_999_999L).header("Authorization", customerAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OD005"));
    }

    @Test
    void 상세도_토큰이_없으면_401_직원_토큰이면_403이다() throws Exception {
        long orderId = orderWithLine(memberId, storeId, "REQUESTED", "아메리카노");

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("C002"));
        // 직원 토큰의 sub가 우연히 이 주문 회원의 id와 같아도 통과하면 안 된다 — 종류로 먼저 막는다.
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId).header("Authorization", staffToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C003"));
    }
}
