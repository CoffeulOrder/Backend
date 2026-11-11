package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.order.application.OrderExpiryJob;
import com.coffeul.support.TestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@TestPropertySource(properties = {"coffeul.dev.payment-flow-enabled=true", "coffeul.payment.gateway=fake"})
class DevOrderFlowIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtAccessTokenService tokens;
    @Autowired private OrderExpiryJob expiryJob;

    private TestFixtures fixtures;
    private long schoolId;
    private long merchantId;
    private long storeId;
    private long memberId;
    private long menuId;
    private long optionId;
    private String customer;
    private String owner;

    @BeforeEach
    void setUp() throws Exception {
        fixtures = new TestFixtures(jdbc);
        String unique = UUID.randomUUID().toString();
        schoolId = fixtures.createSchool("통합흐름-" + unique);
        merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & 0x7fffffff),
                new BigDecimal("0.0300"));
        jdbc.update("INSERT INTO merchant_pg (merchant_id,pg_provider,client_key,secret_ref) "
                + "VALUES (?,'FAKE','fake_client_key_local_only','fake://local-only')", merchantId);
        storeId = fixtures.createStore(merchantId, schoolId, "통합흐름매장", "OPEN");
        String email = unique + "@test.ac.kr";
        memberId = fixtures.createMemberWithPassword(schoolId, email, "password1234", "ACTIVE");
        String loginId = "owner-" + unique;
        fixtures.createStaffAccount(merchantId, "OWNER", loginId, "password1234", "테스트사장님", "ACTIVE");
        // 실제 로그인 API에서 받은 토큰으로 흐름을 시작한다.
        customer = login("/api/v1/auth/login", Map.of("email", email, "password", "password1234"));
        owner = login("/api/v1/auth/staff/login", Map.of("loginId", loginId, "password", "password1234"));
        menuId = fixtures.createMenuItem(storeId, "테스트라떼", 4000, false);
        long groupId = fixtures.createOptionGroup(menuId, "사이즈", "SIZE", true, 1, 1);
        optionId = fixtures.createOptionItem(groupId, "L", 500, false);
    }

    @Test
    void customerOrderReachesManagerBoardAndPickupWithoutFixturePromotion() throws Exception {
        String key = UUID.randomUUID().toString();
        JsonNode order = createOrder(key);
        long id = order.path("orderId").asLong();
        String pgOrderId = order.path("payment").path("pgOrderId").asText();
        createOrder(key);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE order_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(paymentStatus(id)).isEqualTo("READY");
        assertThat(order.path("totalAmount").asInt()).isEqualTo(4500);

        confirm(id, pgOrderId, customer).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.simulated").value(true))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.pickupNo").value(1));
        assertThat(paymentStatus(id)).isEqualTo("APPROVED");
        confirm(id, pgOrderId, customer).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='REQUESTED'",
                Integer.class, id)).isEqualTo(1);

        mvc.perform(get("/api/v1/staff/stores/{storeId}/orders/active", storeId).header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.orders[0].orderId").value(id));
        for (String action : List.of("accept", "start", "ready", "complete")) {
            mvc.perform(post("/api/v1/staff/orders/{orderId}/{action}", id, action).header("Authorization", owner))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/orders/{orderId}", id).header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.pickupNo").value(1))
                .andExpect(jsonPath("$.data.timeline.completedAt").isNotEmpty());
        confirm(id, pgOrderId, customer).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void missingFakeConfigRollsBackWholeOrder() throws Exception {
        jdbc.update("DELETE FROM merchant_pg WHERE merchant_id = ?", merchantId);
        mvc.perform(post("/api/v1/orders").header("Authorization", customer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PY006"));
        assertThat(fixtures.countOrdersByMember(memberId)).isZero();
    }

    @Test
    void wrongCustomerAndPaymentReferenceCannotConfirmOrder() throws Exception {
        JsonNode order = createOrder(UUID.randomUUID().toString());
        long id = order.path("orderId").asLong();
        String pgId = order.path("payment").path("pgOrderId").asText();
        long other = fixtures.createMember(schoolId, UUID.randomUUID() + "@test.ac.kr", "ACTIVE");
        String otherAuth = "Bearer " + tokens.encode(new AuthUser(other, AuthUser.SubjectType.MEMBER,
                AuthUser.Role.CUSTOMER, schoolId, null), Instant.now());
        confirm(id, pgId, otherAuth).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OD005"));
        confirm(id, "not-this-payment", customer).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PY005"));
        confirm(id, pgId, owner).andExpect(status().isForbidden());
        confirm(id, pgId, "Bearer invalid").andExpect(status().isUnauthorized());
        assertThat(paymentStatus(id)).isEqualTo("READY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "WITHDRAWN"})
    void inactiveCustomerCannotConfirm(String memberStatus) throws Exception {
        JsonNode order = createOrder(UUID.randomUUID().toString());
        long id = order.path("orderId").asLong();
        jdbc.update("UPDATE member SET status = ? WHERE id = ?", memberStatus, memberId);
        confirm(id, order.path("payment").path("pgOrderId").asText(), customer)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_005"));
        assertThat(paymentStatus(id)).isEqualTo("READY");
    }

    @Test
    void expiredOrderCannotBeApprovedAndExpiryClosesReadyAttempt() throws Exception {
        JsonNode order = createOrder(UUID.randomUUID().toString());
        long id = order.path("orderId").asLong();
        String pgId = order.path("payment").path("pgOrderId").asText();
        fixtures.ageOrderExpiresAt(id, Instant.now().minusSeconds(30));
        confirm(id, pgId, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OD009"));
        assertThat(paymentStatus(id)).isEqualTo("READY");
        assertThat(expiryJob.expireOne(id, Instant.now())).isTrue();
        assertThat(paymentStatus(id)).isEqualTo("FAILED");
        confirm(id, pgId, customer).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLOSED", "MENU_SOLD_OUT", "OPTION_SOLD_OUT", "AMOUNT_MISMATCH"})
    void changedConditionsDoNotApprovePayment(String condition) throws Exception {
        JsonNode order = createOrder(UUID.randomUUID().toString());
        long id = order.path("orderId").asLong();
        String expectedCode;
        switch (condition) {
            case "CLOSED" -> { jdbc.update("UPDATE store SET status='CLOSED' WHERE id=?", storeId); expectedCode="OD001"; }
            case "MENU_SOLD_OUT" -> { jdbc.update("UPDATE menu_item SET is_sold_out=TRUE WHERE id=?", menuId); expectedCode="OD002"; }
            case "OPTION_SOLD_OUT" -> { jdbc.update("UPDATE option_item SET is_sold_out=TRUE WHERE id=?", optionId); expectedCode="OD003"; }
            default -> { jdbc.update("UPDATE payment SET amount=amount+1 WHERE order_id=?", id); expectedCode="PY001"; }
        }
        confirm(id, order.path("payment").path("pgOrderId").asText(), customer)
                .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.code").value(expectedCode));
        assertThat(paymentStatus(id)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void duplicateConcurrentConfirmIssuesOnePickupNumber() throws Exception {
        JsonNode order = createOrder(UUID.randomUUID().toString());
        long id = order.path("orderId").asLong();
        String pgId = order.path("payment").path("pgOrderId").asText();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> confirm(id, pgId, customer).andReturn().getResponse().getStatus());
            var second = pool.submit(() -> confirm(id, pgId, customer).andReturn().getResponse().getStatus());
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT last_pickup_no FROM order_daily_counter WHERE store_id=?",
                Integer.class, storeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='REQUESTED'",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void concurrentOrdersHaveDifferentPickupNumbers() throws Exception {
        JsonNode first = createOrder(UUID.randomUUID().toString());
        JsonNode second = createOrder(UUID.randomUUID().toString());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> confirm(first.path("orderId").asLong(), first.path("payment").path("pgOrderId").asText(), customer)
                    .andReturn().getResponse().getStatus());
            var b = pool.submit(() -> confirm(second.path("orderId").asLong(), second.path("payment").path("pgOrderId").asText(), customer)
                    .andReturn().getResponse().getStatus());
            assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(jdbc.queryForList("SELECT pickup_no FROM orders WHERE store_id=?", Integer.class, storeId))
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void pickupCounterFailureRollsBackPaymentAndOrder() throws Exception {
        JsonNode order = createOrder(UUID.randomUUID().toString());
        long id = order.path("orderId").asLong();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        jdbc.update("INSERT INTO order_daily_counter (store_id,business_date,last_pickup_no) VALUES (?,?,?)",
                storeId, today, Short.MAX_VALUE);
        confirm(id, order.path("payment").path("pgOrderId").asText(), customer)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("C007"));
        assertThat(paymentStatus(id)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT last_pickup_no FROM order_daily_counter WHERE store_id=? AND business_date=?",
                Integer.class, storeId, today)).isEqualTo((int) Short.MAX_VALUE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='REQUESTED'",
                Integer.class, id)).isZero();
    }

    @Test
    void fakeConfigSeedCanUpgradeExistingDevelopmentMerchantIdempotently() throws Exception {
        jdbc.update("DELETE FROM merchant_pg WHERE merchant_id=?", merchantId);
        String businessRegNo = jdbc.queryForObject("SELECT business_reg_no FROM merchant WHERE id=?", String.class, merchantId);
        String seed = Files.readString(Path.of("docker/dev-seed/fake-payment-config.sql"))
                .replace("'9999999999'", "?");
        jdbc.update(seed, businessRegNo);
        jdbc.update(seed, businessRegNo);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_pg WHERE merchant_id=? AND pg_provider='FAKE'",
                Integer.class, merchantId)).isEqualTo(1);
        JsonNode order = createOrder(UUID.randomUUID().toString());
        confirm(order.path("orderId").asLong(), order.path("payment").path("pgOrderId").asText(), customer)
                .andExpect(status().isOk());
    }

    private String login(String path, Map<String, String> body) throws Exception {
        String response = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(response).path("data").path("accessToken").asText();
    }

    private String orderBody() throws Exception {
        return mapper.writeValueAsString(Map.of("storeId", storeId,
                "items", List.of(Map.of("menuId", menuId, "quantity", 1, "optionItemIds", List.of(optionId))),
                "withdrawalLimitAgreed", true));
    }

    private JsonNode createOrder(String key) throws Exception {
        var response = mvc.perform(post("/api/v1/orders").header("Authorization", customer)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(orderBody()))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("data");
    }

    private ResultActions confirm(long id, String pgId, String authorization) throws Exception {
        return mvc.perform(post("/api/v1/dev/orders/{orderId}/confirm-payment", id)
                .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("pgOrderId", pgId))));
    }

    private String paymentStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM payment WHERE order_id=?", String.class, orderId);
    }
}
