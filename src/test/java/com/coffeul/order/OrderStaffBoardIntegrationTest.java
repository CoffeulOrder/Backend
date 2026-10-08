package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MS-20(진행 중 주문 목록 · 폴링) · MS-21(날짜별 주문 내역) 엔드투엔드 검증. */
class OrderStaffBoardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    private TestFixtures fixtures;
    private long schoolId;
    private long storeId;
    private long memberId;
    private long menuItemId;
    private long sizeLOptionId;
    private String staffAuthHeader;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        schoolId = fixtures.createSchool("테스트대학교-" + unique);
        long merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & 0x7fffffff),
                new BigDecimal("0.0300"));
        storeId = fixtures.createStore(merchantId, schoolId, "테스트매장", "OPEN");
        memberId = fixtures.createMember(schoolId, "member-" + unique + "@test.ac.kr", "ACTIVE");

        menuItemId = fixtures.createMenuItem(storeId, "아메리카노", 4000, false);
        long sizeGroupId = fixtures.createOptionGroup(menuItemId, "사이즈", "SIZE", true, 1, 1);
        fixtures.createOptionItem(sizeGroupId, "R", 0, true);
        sizeLOptionId = fixtures.createOptionItem(sizeGroupId, "L", 1000, false);

        long staffId = fixtures.createStaffAccount(merchantId, "STAFF", "staff-" + unique, "pw1234!!", "테스트직원", "ACTIVE");
        fixtures.createStaffStore(staffId, storeId);
        staffAuthHeader = "Bearer " + jwtAccessTokenService.encode(
                new AuthUser(staffId, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, merchantId), Instant.now());
    }

    /** MS-16으로 실제 order_line · order_line_option을 만든 뒤 원하는 상태로 승격한다. */
    private long createOrderWithLines(String status) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "storeId", storeId,
                "items", List.of(Map.of("menuId", menuItemId, "quantity", 2, "optionItemIds", List.of(sizeLOptionId))),
                "withdrawalLimitAgreed", true));
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("X-Member-Id", memberId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("orderId").asLong();
        fixtures.promoteOrderStatus(orderId, status);
        return orderId;
    }

    @Test
    void 진행중_주문목록에_메뉴와_옵션과_경과시간이_담긴다() throws Exception {
        long orderId = createOrderWithLines("REQUESTED");

        mockMvc.perform(get("/api/v1/staff/stores/{storeId}/orders/active", storeId).header("Authorization", staffAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serverTime").exists())
                .andExpect(jsonPath("$.data.orders.length()").value(1))
                .andExpect(jsonPath("$.data.orders[0].orderId").value(orderId))
                .andExpect(jsonPath("$.data.orders[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.orders[0].items[0].menuName").value("아메리카노"))
                .andExpect(jsonPath("$.data.orders[0].items[0].options[0]").value("L"))
                .andExpect(jsonPath("$.data.orders[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.orders[0].elapsedSeconds").exists());
    }

    @Test
    void 완료_취소_거절된_주문은_진행중_목록에_안_나온다() throws Exception {
        fixtures.createOrder(memberId, storeId, schoolId, "COMPLETED");
        fixtures.createOrder(memberId, storeId, schoolId, "CANCELED");
        fixtures.createOrder(memberId, storeId, schoolId, "REJECTED");
        fixtures.createOrder(memberId, storeId, schoolId, "PENDING_PAYMENT");
        long requestedId = fixtures.createOrder(memberId, storeId, schoolId, "REQUESTED");

        mockMvc.perform(get("/api/v1/staff/stores/{storeId}/orders/active", storeId).header("Authorization", staffAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders.length()").value(1))
                .andExpect(jsonPath("$.data.orders[0].orderId").value(requestedId));
    }

    @Test
    void 다른_매장_직원이면_진행중_목록도_403이다() throws Exception {
        long otherMerchantId = fixtures.createMerchant(
                String.format("%010d", UUID.randomUUID().toString().hashCode() & 0x7fffffff), new BigDecimal("0.0300"));
        long otherStaffId = fixtures.createStaffAccount(otherMerchantId, "STAFF", "other-" + UUID.randomUUID(),
                "pw1234!!", "다른매장직원", "ACTIVE");
        String otherStaffAuth = "Bearer " + jwtAccessTokenService.encode(
                new AuthUser(otherStaffId, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, otherMerchantId), Instant.now());

        mockMvc.perform(get("/api/v1/staff/stores/{storeId}/orders/active", storeId).header("Authorization", otherStaffAuth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test
    void 날짜별_내역을_상태별_건수와_함께_돌려준다() throws Exception {
        fixtures.createOrder(memberId, storeId, schoolId, "COMPLETED");
        fixtures.createOrder(memberId, storeId, schoolId, "CANCELED");
        fixtures.createOrder(memberId, storeId, schoolId, "REJECTED");
        fixtures.createOrder(memberId, storeId, schoolId, "REQUESTED");
        fixtures.createOrder(memberId, storeId, schoolId, "PENDING_PAYMENT"); // business_date 없음 — 내역에 안 잡혀야 함

        mockMvc.perform(get("/api/v1/staff/stores/{storeId}/orders", storeId).header("Authorization", staffAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.completed").value(1))
                .andExpect(jsonPath("$.data.summary.canceled").value(1))
                .andExpect(jsonPath("$.data.summary.rejected").value(1))
                .andExpect(jsonPath("$.data.summary.inProgress").value(1))
                .andExpect(jsonPath("$.data.orders.length()").value(4));
    }

    @Test
    void 날짜_형식이_잘못되면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/staff/stores/{storeId}/orders", storeId)
                        .header("Authorization", staffAuthHeader)
                        .param("date", "2026/13/40"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }
}
