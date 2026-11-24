package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.payment.application.PaymentCancellationProcessor;
import com.coffeul.support.TestFixtures;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@TestPropertySource(properties = {"coffeul.dev.payment-flow-enabled=true", "coffeul.payment.gateway=fake",
        "coffeul.payment.cancel.batch-size=100"})
class OrderRejectionIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtAccessTokenService tokens;
    @Autowired private PaymentCancellationProcessor processor;

    private TestFixtures fixtures;
    private long schoolId;
    private long merchantId;
    private long storeId;
    private long memberId;
    private long menuId;
    private long staffId;
    private long ownerId;
    private String customer;
    private String owner;
    private String staff;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbc);
        String unique = UUID.randomUUID().toString();
        schoolId = fixtures.createSchool("매장 거절-" + unique);
        merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & Integer.MAX_VALUE),
                new BigDecimal("0.0300"));
        jdbc.update("INSERT INTO merchant_pg (merchant_id,pg_provider,client_key,secret_ref) "
                + "VALUES (?,'FAKE','fake_client_key_local_only','fake://local-only')", merchantId);
        storeId = fixtures.createStore(merchantId, schoolId, "거절 테스트 매장", "OPEN");
        memberId = fixtures.createMember(schoolId, unique + "@test.ac.kr", "ACTIVE");
        menuId = fixtures.createMenuItem(storeId, "아메리카노", 1500, false);
        customer = authorization(new AuthUser(memberId, AuthUser.SubjectType.MEMBER,
                AuthUser.Role.CUSTOMER, schoolId, null));
        ownerId = fixtures.createStaffAccount(merchantId, "OWNER", "owner-" + unique,
                "password1234", "사장님", "ACTIVE");
        owner = authorization(new AuthUser(ownerId, AuthUser.SubjectType.STAFF, AuthUser.Role.OWNER, null, merchantId));
        staffId = fixtures.createStaffAccount(merchantId, "STAFF", "staff-" + unique,
                "password1234", "직원", "ACTIVE");
        fixtures.createStaffStore(staffId, storeId);
        staff = authorization(new AuthUser(staffId, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, merchantId));
    }

    @Test
    void 매장_거절과_전액환불_상태를_고객과_매장이_조회할수_있다() throws Exception {
        long orderId = approvedOrder();
        reject(orderId, owner, "OTHER", "기기 점검 중이에요").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(202))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reasonCode").value("OTHER"))
                .andExpect(jsonPath("$.data.reasonDetail").value("기기 점검 중이에요"))
                .andExpect(jsonPath("$.data.refundStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.refundAmount").value(3000));
        reject(orderId, owner, "OTHER", "기기 점검 중이에요").andExpect(status().isAccepted());

        mvc.perform(get("/api/v1/orders/{id}", orderId).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rejectReason").value("OTHER"))
                .andExpect(jsonPath("$.data.rejectReasonDetail").value("기기 점검 중이에요"))
                .andExpect(jsonPath("$.data.timeline.rejectedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.refundStatus").value("REQUESTED"));
        mvc.perform(get("/api/v1/staff/stores/{id}/orders/active", storeId).header("Authorization", owner))
                .andExpect(jsonPath("$.data.orders").isEmpty());
        mvc.perform(get("/api/v1/staff/stores/{id}/orders", storeId).header("Authorization", owner))
                .andExpect(jsonPath("$.data.summary.rejected").value(1));

        processor.processBatch();
        reject(orderId, owner, "OTHER", "기기 점검 중이에요").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("SUCCEEDED"));
        assertThat(refundCount(orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='REJECTED'",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT requested_by_type FROM payment_cancel c JOIN payment p ON p.id=c.payment_id WHERE p.order_id=?",
                String.class, orderId)).isEqualTo("STAFF");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOLD_OUT", "INGREDIENT_SHORTAGE", "STORE_BUSY", "STORE_CLOSING", "OTHER"})
    void 기존_DB_거절사유를_배정된_직원이_사용할수_있다(String reason) throws Exception {
        reject(approvedOrder(), staff, reason, "매장 사정").andExpect(status().isAccepted());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "UNKNOWN", "sold_out"})
    void 잘못된_거절사유는_검증오류다(String reason) throws Exception {
        long orderId = approvedOrder();
        reject(orderId, owner, reason, "사유").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
        assertThat(orderStatus(orderId)).isEqualTo("REQUESTED");
        assertThat(refundCount(orderId)).isZero();
    }

    @Test
    void 기타_사유의_메모누락과_너무긴_메모를_거절한다() throws Exception {
        long orderId = approvedOrder();
        reject(orderId, owner, "OTHER", "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reasonDetail"));
        reject(orderId, owner, "SOLD_OUT", "a".repeat(201)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/staff/orders/{id}/reject", orderId).header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(refundCount(orderId)).isZero();
    }

    @Test
    void 고객과_다른매장_직원과_비활성계정은_거절할수_없다() throws Exception {
        long orderId = approvedOrder();
        reject(orderId, customer, "SOLD_OUT", "").andExpect(status().isForbidden());
        jdbc.update("DELETE FROM staff_store WHERE staff_account_id=?", staffId);
        reject(orderId, staff, "SOLD_OUT", "").andExpect(status().isForbidden());
        long otherMerchant = fixtures.createMerchant(String.format("%010d", UUID.randomUUID().hashCode() & Integer.MAX_VALUE),
                new BigDecimal("0.0300"));
        long otherOwnerId = fixtures.createStaffAccount(otherMerchant, "OWNER", "other-" + UUID.randomUUID(),
                "password1234", "다른 사장님", "ACTIVE");
        String other = authorization(new AuthUser(otherOwnerId, AuthUser.SubjectType.STAFF, AuthUser.Role.OWNER, null, otherMerchant));
        reject(orderId, other, "SOLD_OUT", "").andExpect(status().isForbidden());
        jdbc.update("UPDATE staff_account SET status='INACTIVE' WHERE id=?", ownerId);
        reject(orderId, owner, "SOLD_OUT", "").andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/staff/orders/{id}/reject", orderId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"SOLD_OUT\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(orderStatus(orderId)).isEqualTo("REQUESTED");
        assertThat(refundCount(orderId)).isZero();
    }

    @Test
    void 없는_주문은_404이고_수락후에는_거절할수_없다() throws Exception {
        reject(Long.MAX_VALUE, owner, "SOLD_OUT", "").andExpect(status().isNotFound());
        long orderId = approvedOrder();
        mvc.perform(post("/api/v1/staff/orders/{id}/accept", orderId).header("Authorization", owner))
                .andExpect(status().isOk());
        reject(orderId, owner, "SOLD_OUT", "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.currentStatus").value("ACCEPTED"));
        assertThat(refundCount(orderId)).isZero();
    }

    @Test
    void 거절_재요청으로_사유를_덮어쓰거나_환불을_중복할수_없다() throws Exception {
        long orderId = approvedOrder();
        reject(orderId, owner, "SOLD_OUT", "").andExpect(status().isAccepted());
        reject(orderId, owner, "OTHER", "다른 사유").andExpect(status().isConflict());
        assertThat(refundCount(orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reject_reason_code FROM orders WHERE id=?", String.class, orderId))
                .isEqualTo("SOLD_OUT");
    }

    @Test
    void 환불요청을_저장하지_못하면_거절도_롤백한다() throws Exception {
        long orderId = approvedOrder();
        jdbc.update("UPDATE payment SET amount=amount+1 WHERE order_id=?", orderId);
        reject(orderId, owner, "SOLD_OUT", "").andExpect(status().isBadRequest());
        assertThat(orderStatus(orderId)).isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("SELECT reject_reason_code FROM orders WHERE id=?", String.class, orderId)).isNull();
        assertThat(refundCount(orderId)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "cancel", "reject"})
    void 거절이_수락_고객취소_중복거절과_겹쳐도_처리는_일관된다(String otherAction) throws Exception {
        long orderId = approvedOrder();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var rejection = pool.submit(() -> {
                await(ready, go);
                return reject(orderId, owner, "SOLD_OUT", "").andReturn().getResponse().getStatus();
            });
            var other = pool.submit(() -> {
                await(ready, go);
                if ("reject".equals(otherAction)) {
                    return reject(orderId, owner, "SOLD_OUT", "").andReturn().getResponse().getStatus();
                }
                String path = "cancel".equals(otherAction) ? "/api/v1/orders/{id}/cancel" : "/api/v1/staff/orders/{id}/accept";
                return mvc.perform(post(path, orderId).header("Authorization", "cancel".equals(otherAction) ? customer : owner))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Integer> responses = List.of(rejection.get(10, TimeUnit.SECONDS), other.get(10, TimeUnit.SECONDS));
            if ("reject".equals(otherAction)) {
                assertThat(responses).containsExactly(202, 202);
            } else {
                assertThat(responses.stream().filter(code -> code == 409).count()).isEqualTo(1);
                assertThat(responses.stream().filter(code -> code == 200 || code == 202).count()).isEqualTo(1);
            }
        }
        int expectedRefunds = "ACCEPTED".equals(orderStatus(orderId)) ? 0 : 1;
        assertThat(refundCount(orderId)).isEqualTo(expectedRefunds);
    }

    private void await(CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("경합 테스트 대기 실패");
    }

    private long approvedOrder() throws Exception {
        String request = mapper.writeValueAsString(Map.of("storeId", storeId,
                "items", List.of(Map.of("menuId", menuId, "quantity", 2, "optionItemIds", List.of())),
                "withdrawalLimitAgreed", true));
        String body = mvc.perform(post("/api/v1/orders").header("Authorization", customer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var order = mapper.readTree(body).path("data");
        long id = order.path("orderId").asLong();
        mvc.perform(post("/api/v1/dev/orders/{id}/confirm-payment", id).header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("pgOrderId", order.path("payment").path("pgOrderId").asText()))))
                .andExpect(status().isOk());
        return id;
    }

    private ResultActions reject(long orderId, String auth, String reason, String detail) throws Exception {
        return mvc.perform(post("/api/v1/staff/orders/{id}/reject", orderId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("reasonCode", reason, "reasonDetail", detail))));
    }

    private String authorization(AuthUser user) {
        return "Bearer " + tokens.encode(user, Instant.now());
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, orderId);
    }

    private int refundCount(long orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payment_cancel c JOIN payment p ON p.id=c.payment_id WHERE p.order_id=?",
                Integer.class, orderId);
    }
}
