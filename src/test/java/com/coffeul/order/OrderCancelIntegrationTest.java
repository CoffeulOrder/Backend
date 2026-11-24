package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.payment.application.PaymentCancellationProcessor;
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
        "coffeul.payment.cancel.enabled=false", "coffeul.payment.cancel.batch-size=100"})
class OrderCancelIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtAccessTokenService tokens;
    @Autowired private PaymentCancellationProcessor processor;

    private TestFixtures fixtures;
    private long schoolId;
    private long storeId;
    private long memberId;
    private long menuId;
    private String customer;
    private String owner;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbc);
        String unique = UUID.randomUUID().toString();
        schoolId = fixtures.createSchool("취소 테스트-" + unique);
        long merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & Integer.MAX_VALUE),
                new BigDecimal("0.0300"));
        jdbc.update("INSERT INTO merchant_pg (merchant_id,pg_provider,client_key,secret_ref) "
                + "VALUES (?,'FAKE','fake_client_key_local_only','fake://local-only')", merchantId);
        storeId = fixtures.createStore(merchantId, schoolId, "취소 테스트 매장", "OPEN");
        memberId = fixtures.createMember(schoolId, unique + "@test.ac.kr", "ACTIVE");
        menuId = fixtures.createMenuItem(storeId, "아메리카노", 1500, false);
        customer = authorization(new AuthUser(memberId, AuthUser.SubjectType.MEMBER,
                AuthUser.Role.CUSTOMER, schoolId, null));
        long staffId = fixtures.createStaffAccount(merchantId, "OWNER", "owner-" + unique,
                "password1234", "테스트사장님", "ACTIVE");
        owner = authorization(new AuthUser(staffId, AuthUser.SubjectType.STAFF, AuthUser.Role.OWNER, null, merchantId));
    }

    @Test
    void 접수된_주문을_취소하면_전액환불_요청과_이력이_한번만_저장된다() throws Exception {
        long orderId = approvedOrder();

        cancel(orderId, customer).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(202))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.refundStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.refundAmount").value(3000))
                .andExpect(jsonPath("$.data.canceledAt").isNotEmpty());
        cancel(orderId, customer).andExpect(status().isAccepted());

        assertThat(refundCount(orderId)).isEqualTo(1);
        assertThat(cancelHistoryCount(orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE order_id=?", String.class, orderId))
                .isEqualTo("APPROVED");
        mvc.perform(get("/api/v1/orders/{id}", orderId).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.timeline.canceledAt").isNotEmpty())
                .andExpect(jsonPath("$.data.cancelable").value(false));
        mvc.perform(get("/api/v1/staff/stores/{id}/orders/active", storeId).header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orders").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCEPTED", "MAKING", "READY", "COMPLETED", "PENDING_PAYMENT", "EXPIRED"})
    void 수락후와_결제전_상태에서는_취소를_거절한다(String orderStatus) throws Exception {
        long orderId = approvedOrder();
        fixtures.promoteOrderStatus(orderId, orderStatus);

        cancel(orderId, customer).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OD006"))
                .andExpect(jsonPath("$.data.currentStatus").value(orderStatus));
        assertThat(refundCount(orderId)).isZero();
        assertThat(cancelHistoryCount(orderId)).isZero();
        assertThat(orderStatus(orderId)).isEqualTo(orderStatus);
    }

    @Test
    void 개발용_환불완료_후_재요청과_상세조회는_성공상태를_반환한다() throws Exception {
        long orderId = approvedOrder();
        cancel(orderId, customer).andExpect(status().isAccepted());
        processor.processBatch();

        cancel(orderId, customer).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.refundStatus").value("SUCCEEDED"));
        mvc.perform(get("/api/v1/orders/{id}", orderId).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("SUCCEEDED"));
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE order_id=?", String.class, orderId))
                .isEqualTo("CANCELED");
        assertThat(refundCount(orderId)).isEqualTo(1);
    }

    @Test
    void 환불확인이_필요한_취소는_FAILED를_그대로_알린다() throws Exception {
        long orderId = approvedOrder();
        cancel(orderId, customer).andExpect(status().isAccepted());
        jdbc.update("UPDATE payment_cancel c JOIN payment p ON p.id=c.payment_id SET c.status='FAILED' WHERE p.order_id=?",
                orderId);

        cancel(orderId, customer).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundStatus").value("FAILED"))
                .andExpect(jsonPath("$.message").value("주문은 취소됐어요. 환불 처리에 확인이 필요해요."));
        mvc.perform(get("/api/v1/orders/{id}/payment", orderId).header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.orderStatus").value("CANCELED"))
                .andExpect(jsonPath("$.data.payment.pgProvider").value("FAKE"))
                .andExpect(jsonPath("$.data.retryAvailable").value(false))
                .andExpect(jsonPath("$.data.refund.status").value("FAILED"));
        assertThat(refundCount(orderId)).isEqualTo(1);
    }

    @Test
    void 남의_주문과_없는_주문은_같은_404이고_인증과_권한을_검사한다() throws Exception {
        long orderId = approvedOrder();
        long otherId = fixtures.createMember(schoolId, UUID.randomUUID() + "@test.ac.kr", "ACTIVE");
        String other = authorization(new AuthUser(otherId, AuthUser.SubjectType.MEMBER,
                AuthUser.Role.CUSTOMER, schoolId, null));

        cancel(orderId, other).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OD005"));
        cancel(Long.MAX_VALUE, customer).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OD005"));
        cancel(orderId, owner).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("C003"));
        mvc.perform(post("/api/v1/orders/{id}/cancel", orderId))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("C002"));
        assertThat(refundCount(orderId)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "WITHDRAWN"})
    void 사용할수_없는_회원은_취소할수_없다(String memberStatus) throws Exception {
        long orderId = approvedOrder();
        jdbc.update("UPDATE member SET status=? WHERE id=?", memberStatus, memberId);

        cancel(orderId, customer).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_005"));
        assertThat(orderStatus(orderId)).isEqualTo("REQUESTED");
        assertThat(refundCount(orderId)).isZero();
    }

    @Test
    void 환불요청_저장이_실패하면_주문취소도_롤백한다() throws Exception {
        long orderId = approvedOrder();
        jdbc.update("UPDATE payment SET amount=amount+1 WHERE order_id=?", orderId);

        cancel(orderId, customer).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PY001"));
        assertThat(orderStatus(orderId)).isEqualTo("REQUESTED");
        assertThat(cancelHistoryCount(orderId)).isZero();
        assertThat(refundCount(orderId)).isZero();
    }

    @Test
    void 취소사유는_이력에_남고_너무_긴_사유는_거절한다() throws Exception {
        long orderId = approvedOrder();
        mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reason", "a".repeat(201)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        assertThat(orderStatus(orderId)).isEqualTo("REQUESTED");

        mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"메뉴를 잘못 골랐어요\"}"))
                .andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT reason FROM order_status_history WHERE order_id=? AND to_status='CANCELED'",
                String.class, orderId)).isEqualTo("메뉴를 잘못 골랐어요");
    }

    @Test
    void 동시_취소도_한번만_환불을_요청한다() throws Exception {
        long orderId = approvedOrder();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> cancelAfterLatch(orderId, ready, go));
            var second = pool.submit(() -> cancelAfterLatch(orderId, ready, go));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(202);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(202);
        }
        assertThat(refundCount(orderId)).isEqualTo(1);
        assertThat(cancelHistoryCount(orderId)).isEqualTo(1);
    }

    @Test
    void 매장수락과_고객취소가_경합하면_하나만_성공한다() throws Exception {
        long orderId = approvedOrder();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var cancellation = pool.submit(() -> cancelAfterLatch(orderId, ready, go));
            var acceptance = pool.submit(() -> {
                ready.countDown();
                if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("경합 테스트 대기 실패");
                return mvc.perform(post("/api/v1/staff/orders/{id}/accept", orderId).header("Authorization", owner))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            int cancelStatus = cancellation.get(10, TimeUnit.SECONDS);
            int acceptStatus = acceptance.get(10, TimeUnit.SECONDS);
            if (cancelStatus == 202) {
                assertThat(acceptStatus).isEqualTo(409);
                assertThat(orderStatus(orderId)).isEqualTo("CANCELED");
                assertThat(refundCount(orderId)).isEqualTo(1);
            } else {
                assertThat(cancelStatus).isEqualTo(409);
                assertThat(acceptStatus).isEqualTo(200);
                assertThat(orderStatus(orderId)).isEqualTo("ACCEPTED");
                assertThat(refundCount(orderId)).isZero();
            }
        }
    }

    private int cancelAfterLatch(long orderId, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("경합 테스트 대기 실패");
        return cancel(orderId, customer).andReturn().getResponse().getStatus();
    }

    private long approvedOrder() throws Exception {
        String request = mapper.writeValueAsString(Map.of("storeId", storeId,
                "items", List.of(Map.of("menuId", menuId, "quantity", 2, "optionItemIds", List.of())),
                "withdrawalLimitAgreed", true));
        String body = mvc.perform(post("/api/v1/orders").header("Authorization", customer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode order = mapper.readTree(body).path("data");
        long orderId = order.path("orderId").asLong();
        mvc.perform(post("/api/v1/dev/orders/{id}/confirm-payment", orderId).header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("pgOrderId", order.path("payment").path("pgOrderId").asText()))))
                .andExpect(status().isOk());
        return orderId;
    }

    private ResultActions cancel(long orderId, String auth) throws Exception {
        return mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).header("Authorization", auth));
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

    private int cancelHistoryCount(long orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='CANCELED'",
                Integer.class, orderId);
    }
}
