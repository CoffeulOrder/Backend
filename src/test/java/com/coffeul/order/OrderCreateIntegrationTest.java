package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MS-16(주문 생성) 엔드투엔드 검증 — 실제 MySQL 컨테이너에 주문·주문항목·옵션이
 * 정확한 금액으로 저장되는지, 그리고 같은 Idempotency-Key 재요청이 중복 주문을 만들지 않는지 확인한다.
 * 돈이 걸린 기능이라 "응답이 200이다"가 아니라 "DB에 실제로 무엇이 남았는가"를 기준으로 검증한다.
 */
class OrderCreateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private TestFixtures fixtures;
    private long memberId;
    private long storeId;
    private long menuItemId;
    private long sizeLOptionId;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);

        // 테스트 메서드끼리 같은 컨테이너·DB를 공유하므로 UNIQUE 제약과 충돌하지 않게 매번 고유값을 쓴다.
        String unique = UUID.randomUUID().toString().substring(0, 8);
        long schoolId = fixtures.createSchool("테스트대학교-" + unique);
        long merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & 0x7fffffff),
                new BigDecimal("0.0300"));
        storeId = fixtures.createStore(merchantId, schoolId, "테스트매장", "OPEN");
        memberId = fixtures.createMember(schoolId, "member-" + unique + "@test.ac.kr", "ACTIVE");

        menuItemId = fixtures.createMenuItem(storeId, "아메리카노", 4000, false);
        long sizeGroupId = fixtures.createOptionGroup(menuItemId, "사이즈", "SIZE", true, 1, 1);
        fixtures.createOptionItem(sizeGroupId, "R", 0, true);
        sizeLOptionId = fixtures.createOptionItem(sizeGroupId, "L", 1000, false);
    }

    @Test
    void 주문을_생성하면_주문과_주문항목이_정확한_금액으로_저장된다() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "storeId", storeId,
                "items", List.of(Map.of(
                        "menuId", menuItemId,
                        "quantity", 2,
                        "optionItemIds", List.of(sizeLOptionId))),
                "withdrawalLimitAgreed", true));

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("X-Member-Id", memberId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.totalAmount").value(10000))
                .andReturn();

        String orderCode = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("orderCode").asText();

        Map<String, Object> orderRow = jdbcTemplate.queryForMap(
                "SELECT * FROM `orders` WHERE `order_code` = ?", orderCode);
        assertThat(orderRow.get("member_id")).isEqualTo(memberId);
        assertThat(orderRow.get("subtotal_amount")).isEqualTo(10000);
        assertThat(orderRow.get("total_amount")).isEqualTo(10000);
        assertThat(orderRow.get("status")).isEqualTo("PENDING_PAYMENT");

        Map<String, Object> lineRow = jdbcTemplate.queryForMap(
                "SELECT * FROM `order_line` WHERE `order_id` = ?", orderRow.get("id"));
        assertThat(lineRow.get("unit_price")).isEqualTo(5000);
        assertThat(lineRow.get("quantity")).isEqualTo(2);
        assertThat(lineRow.get("line_amount")).isEqualTo(10000);

        Map<String, Object> optionRow = jdbcTemplate.queryForMap(
                "SELECT * FROM `order_line_option` WHERE `order_line_id` = ?", lineRow.get("id"));
        assertThat(optionRow.get("option_item_name_snapshot")).isEqualTo("L");
        assertThat(optionRow.get("price_delta_snapshot")).isEqualTo(1000);
    }

    @Test
    void 같은_Idempotency_Key로_재요청하면_주문이_중복_생성되지_않는다() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "storeId", storeId,
                "items", List.of(Map.of(
                        "menuId", menuItemId,
                        "quantity", 1,
                        "optionItemIds", List.of(sizeLOptionId))),
                "withdrawalLimitAgreed", true));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Member-Id", memberId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Member-Id", memberId)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(5000));

        assertThat(fixtures.countOrdersByMember(memberId)).isEqualTo(1);
    }

    @Test
    void 품절_메뉴를_주문하면_주문이_저장되지_않는다() throws Exception {
        long soldOutMenuId = fixtures.createMenuItem(storeId, "품절메뉴", 3000, true);
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "storeId", storeId,
                "items", List.of(Map.of(
                        "menuId", soldOutMenuId,
                        "quantity", 1,
                        "optionItemIds", List.of())),
                "withdrawalLimitAgreed", true));

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Member-Id", memberId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().is4xxClientError());

        assertThat(fixtures.countOrdersByMember(memberId)).isEqualTo(0);
    }
}
