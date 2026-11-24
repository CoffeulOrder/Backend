package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.common.error.BusinessException;
import com.coffeul.order.application.ExpiredPaymentRecoveryJob;
import com.coffeul.order.application.OrderExpiryJob;
import com.coffeul.payment.api.PaymentCancellationApi;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.api.PaymentRecoveryApi;
import com.coffeul.payment.application.PaymentCancellationProcessor;
import com.coffeul.payment.infrastructure.PortOnePaymentClient;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@TestPropertySource(properties = {"coffeul.payment.gateway=portone", "coffeul.payment.cancel.batch-size=100",
        "coffeul.payment.recovery.batch-size=100", "coffeul.payment.portone.webhook-enabled=true",
        "coffeul.payment.portone.webhook-secret=whsec_" + PortOneOrderPaymentIntegrationTest.TEST_SIGNING_KEY})
class ExpiredPaymentRecoveryIntegrationTest extends AbstractIntegrationTest {
    private static final String STORE = "store-recovery-test";
    private static final String CHANNEL = "channel-recovery-test";
    private static final int AMOUNT = 3000;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtAccessTokenService tokens;
    @Autowired private OrderExpiryJob expiry;
    @Autowired private ExpiredPaymentRecoveryJob job;
    @Autowired private PaymentRecoveryApi recovery;
    @Autowired private PaymentCancellationProcessor refunds;
    @Autowired private TransactionTemplate transaction;
    @MockitoBean private PortOnePaymentClient client;
    @MockitoSpyBean private PaymentCancellationApi cancellations;
    private TestFixtures fixtures;
    private long schoolId;
    private long memberId;
    private long storeId;
    private long menuId;
    private String customer;

    @BeforeEach
    void setUp() {
        // 타 테스트의 주문은 보존하고 이 테스트의 수동 작업 호출과 섞이지 않게 미룬다.
        transaction.executeWithoutResult(status -> recovery.discover(Instant.now(), 10000));
        jdbc.update("UPDATE payment_reconciliation SET next_retry_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY) WHERE status='WAITING'");
        fixtures = new TestFixtures(jdbc);
        String unique = UUID.randomUUID().toString();
        schoolId = fixtures.createSchool("만료 복구-" + unique);
        long merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & Integer.MAX_VALUE),
                new BigDecimal("0.0300"));
        jdbc.update("""
                INSERT INTO merchant_pg (merchant_id,pg_provider,client_key,secret_ref,portone_store_id)
                VALUES (?,'INICIS_V2',?,'test-fixture',?)
                """, merchantId, CHANNEL, STORE);
        storeId = fixtures.createStore(merchantId, schoolId, "복구 테스트 매장", "OPEN");
        memberId = fixtures.createMember(schoolId, unique + "@test.ac.kr", "ACTIVE");
        menuId = fixtures.createMenuItem(storeId, "카페라떼", 3000, false);
        customer = bearer(memberId);
    }

    @Test
    void expiredAppCallbackCommitsRefundBeforeReturningConflict() throws Exception {
        var order = expiredOrder();
        paid(order);
        confirm(order, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OD009"))
                .andExpect(jsonPath("$.data.refundStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.refundAmount").value(AMOUNT));
        assertRefundQueued(order);
        mvc.perform(get("/api/v1/orders/{id}", order.id()).header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refundStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.cancelable").value(false)).andExpect(jsonPath("$.data.pickupNo").isEmpty());

        when(client.cancelFullPayment(any(), anyString(), eq("ADMIN"), eq("주문 만료 후 결제 확인"))).thenReturn(true);
        refunds.processBatch();
        confirm(order, customer).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.refundStatus").value("SUCCEEDED"));
        assertThat(paymentStatus(order)).isEqualTo("CANCELED");
        verify(client, times(1)).getPaidPayment(order.paymentId(), STORE);
    }

    @Test
    void overdueOrderIsExpiredAndRefundedWithoutWaitingForExpiryJob() throws Exception {
        var order = createOrder();
        fixtures.ageOrderExpiresAt(order.id(), Instant.now().minusSeconds(2));
        paid(order);
        confirm(order, customer).andExpect(status().isConflict());
        assertRefundQueued(order);
        assertThat(expiry.expireOne(order.id(), Instant.now())).isFalse();
        assertThat(count("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='EXPIRED'", order.id()))
                .isEqualTo(1);
    }

    @Test
    void webhookCanRefundExpiredOrderEvenIfMemberIsInactive() throws Exception {
        var order = expiredOrder();
        jdbc.update("UPDATE member SET status='SUSPENDED' WHERE id=?", memberId);
        paid(order);
        webhook(order, STORE).andExpect(status().isNoContent());
        webhook(order, STORE).andExpect(status().isNoContent());
        assertRefundQueued(order);
        verify(client, times(1)).getPaidPayment(order.paymentId(), STORE);
    }

    @Test
    void unauthorizedCallbackCannotStartProviderLookupOrRefund() throws Exception {
        var order = expiredOrder();
        long other = fixtures.createMember(schoolId, UUID.randomUUID() + "@test.ac.kr", "ACTIVE");
        confirm(order, bearer(other)).andExpect(status().isNotFound());
        webhook(order, "wrong-store").andExpect(status().isConflict());
        verifyNoInteractions(client);
        assertThat(refundCount(order)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY", "UNKNOWN", "APPROVED"})
    void scheduledRecoveryFindsPaidOrderWithoutAnyCallback(String paymentState) throws Exception {
        var order = expiredOrder();
        if ("APPROVED".equals(paymentState)) {
            jdbc.update("UPDATE payment SET status='APPROVED',payment_key=?,approved_at=UTC_TIMESTAMP(6) WHERE order_id=?",
                    "transaction-" + order.id(), order.id());
        } else {
            jdbc.update("UPDATE payment SET status=? WHERE order_id=?", paymentState, order.id());
            paid(order);
        }
        assertThat(job.processBatch()).isEqualTo(1);
        assertRefundQueued(order);
        assertThat(recoveryStatus(order)).isEqualTo("COMPLETE");
        assertThat(job.processBatch()).isZero();
    }

    @Test
    void unpaidAndTemporaryFailureAreRetriedUntilLatePaymentIsFound() throws Exception {
        var order = expiredOrder();
        when(client.getPaidPayment(order.paymentId(), STORE))
                .thenThrow(new BusinessException(PaymentErrorCode.NOT_PAID))
                .thenThrow(new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE))
                .thenReturn(payment(order, AMOUNT, CHANNEL));
        job.processBatch();
        assertThat(recoveryStatus(order)).isEqualTo("WAITING");
        assertThat(errorCode(order)).isEqualTo("PY009");
        assertThat(refundCount(order)).isZero();
        assertThat(job.processBatch()).isZero();
        dueNow(order);
        job.processBatch();
        assertThat(errorCode(order)).isEqualTo("PY010");
        dueNow(order);
        job.processBatch();
        assertRefundQueued(order);
        assertThat(recoveryStatus(order)).isEqualTo("COMPLETE");
        assertThat(count("SELECT attempt_count FROM payment_reconciliation WHERE payment_id=(SELECT id FROM payment WHERE order_id=?)", order.id()))
                .isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "channel"})
    void providerMismatchIsKeptForManualReviewWithoutRefund(String mismatch) throws Exception {
        var order = expiredOrder();
        when(client.getPaidPayment(order.paymentId(), STORE)).thenReturn(payment(order,
                "amount".equals(mismatch) ? AMOUNT + 1 : AMOUNT, "channel".equals(mismatch) ? "other" : CHANNEL));
        job.processBatch();
        assertThat(recoveryStatus(order)).isEqualTo("FAILED");
        assertThat(errorCode(order)).isEqualTo("amount".equals(mismatch) ? "PY001" : "PY011");
        assertThat(refundCount(order)).isZero();
        assertThat(paymentStatus(order)).isEqualTo("READY");
    }

    @Test
    void failedRefundEnqueueRollsBackApprovalAndExpiryThenCanRetry() throws Exception {
        var order = createOrder();
        fixtures.ageOrderExpiresAt(order.id(), Instant.now().minusSeconds(2));
        paid(order);
        PaymentCancellationApi target = AopTestUtils.getUltimateTargetObject(cancellations);
        doThrow(new BusinessException(PaymentErrorCode.SETUP_NOT_READY)).when(target)
                .requestExpiredPayment(order.id(), AMOUNT);
        confirm(order, customer).andExpect(status().isServiceUnavailable());
        assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(paymentStatus(order)).isEqualTo("READY");
        assertThat(refundCount(order)).isZero();
        assertThat(count("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='EXPIRED'", order.id()))
                .isZero();
        doCallRealMethod().when(target).requestExpiredPayment(order.id(), AMOUNT);
        confirm(order, customer).andExpect(status().isConflict());
        assertRefundQueued(order);
    }

    @Test
    void workerLeasePreventsConcurrentProviderLookupAndDoesNotHoldTransaction() throws Exception {
        var order = expiredOrder();
        var lookedUp = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        when(client.getPaidPayment(order.paymentId(), STORE)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            lookedUp.countDown();
            assertThat(finish.await(10, TimeUnit.SECONDS)).isTrue();
            return payment(order, AMOUNT, CHANNEL);
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(job::processBatch);
            assertThat(lookedUp.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThat(pool.submit(job::processBatch).get(5, TimeUnit.SECONDS)).isZero();
            } finally {
                finish.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertRefundQueued(order);
        verify(client, times(1)).getPaidPayment(order.paymentId(), STORE);
    }

    @Test
    void webhookAndRecoveryRaceCreateOneRefundAndNoPickupNumber() throws Exception {
        var order = expiredOrder();
        var both = new CountDownLatch(2);
        when(client.getPaidPayment(order.paymentId(), STORE)).thenAnswer(call -> {
            both.countDown();
            assertThat(both.await(5, TimeUnit.SECONDS)).isTrue();
            return payment(order, AMOUNT, CHANNEL);
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var worker = pool.submit(job::processBatch);
            var hook = pool.submit(() -> webhook(order, STORE).andReturn().getResponse().getStatus());
            assertThat(worker.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(hook.get(10, TimeUnit.SECONDS)).isEqualTo(204);
        }
        assertRefundQueued(order);
    }

    @Test
    void expiredLeaseCanBeReclaimedAndStaleResultsCannotOverrideNewAttempt() throws Exception {
        var order = expiredOrder();
        Instant now = Instant.now();
        transaction.executeWithoutResult(status -> recovery.discover(now, 100));
        long paymentId = jdbc.queryForObject("SELECT id FROM payment WHERE order_id=?", Long.class, order.id());
        var first = transaction.execute(status -> recovery.claim(paymentId, now, now.plusSeconds(30))).orElseThrow();
        var duplicate = transaction.execute(status -> recovery.claim(paymentId, now, now.plusSeconds(30)));
        assertThat(duplicate).isEmpty();
        var second = transaction.execute(status -> recovery.claim(paymentId, now.plusSeconds(31), now.plusSeconds(61))).orElseThrow();
        recovery.complete(first);
        recovery.fail(first, "STALE");
        recovery.retry(first, now, "STALE");
        assertThat(recoveryStatus(order)).isEqualTo("WAITING");
        assertThat(errorCode(order)).isNull();
        assertThat(second.attempt()).isEqualTo(2);
        recovery.complete(second);
        assertThat(recoveryStatus(order)).isEqualTo("COMPLETE");
    }

    @Test
    void unexpiredOrderIsNotDiscovered() throws Exception {
        var order = createOrder();
        assertThat(job.processBatch()).isZero();
        assertThat(count("SELECT COUNT(*) FROM payment_reconciliation WHERE payment_id=(SELECT id FROM payment WHERE order_id=?)", order.id()))
                .isZero();
        verifyNoInteractions(client);
    }

    private Checkout expiredOrder() throws Exception {
        var order = createOrder();
        fixtures.ageOrderExpiresAt(order.id(), Instant.now().minusSeconds(2));
        assertThat(expiry.expireOne(order.id(), Instant.now())).isTrue();
        return order;
    }

    private Checkout createOrder() throws Exception {
        String body = mvc.perform(post("/api/v1/orders").header("Authorization", customer)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("storeId", storeId, "withdrawalLimitAgreed", true,
                                "items", List.of(Map.of("menuId", menuId, "quantity", 1))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var data = mapper.readTree(body).path("data");
        return new Checkout(data.path("orderId").asLong(), data.at("/payment/portOne/paymentId").asText());
    }

    private ResultActions confirm(Checkout order, String authorization) throws Exception {
        return mvc.perform(post("/api/v1/payments/confirm").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                        Map.of("orderId", order.id(), "paymentId", order.paymentId()))));
    }

    private ResultActions webhook(Checkout order, String store) throws Exception {
        String body = mapper.writeValueAsString(Map.of("type", "Transaction.Paid", "data",
                Map.of("paymentId", order.paymentId(), "storeId", store)));
        String id = "recovery-webhook-id";
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(PortOneOrderPaymentIntegrationTest.TEST_SIGNING_KEY), "HmacSHA256"));
        String signature = "v1," + Base64.getEncoder().encodeToString(
                mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        return mvc.perform(post("/api/v1/payments/webhooks/portone").contentType(MediaType.APPLICATION_JSON)
                .header("webhook-id", id).header("webhook-timestamp", timestamp)
                .header("webhook-signature", signature).content(body));
    }

    private void paid(Checkout order) {
        when(client.getPaidPayment(order.paymentId(), STORE)).thenReturn(payment(order, AMOUNT, CHANNEL));
    }

    private PortOnePaymentClient.PaidPayment payment(Checkout order, int amount, String channel) {
        return new PortOnePaymentClient.PaidPayment(order.paymentId(), "transaction-" + order.id(), STORE,
                channel, "INICIS_V2", "V2", "KRW", amount, "CARD", Instant.now());
    }

    private void assertRefundQueued(Checkout order) {
        assertThat(orderStatus(order)).isEqualTo("EXPIRED");
        assertThat(refundCount(order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT requested_by_type FROM payment_cancel c JOIN payment p ON p.id=c.payment_id WHERE p.order_id=?",
                String.class, order.id())).isEqualTo("SYSTEM");
        assertThat(count("SELECT COUNT(*) FROM orders WHERE id=? AND pickup_no IS NOT NULL", order.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='REQUESTED'", order.id())).isZero();
    }

    private void dueNow(Checkout order) {
        jdbc.update("UPDATE payment_reconciliation SET next_retry_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE payment_id=(SELECT id FROM payment WHERE order_id=?)", order.id());
    }

    private String recoveryStatus(Checkout order) {
        return jdbc.queryForObject("SELECT status FROM payment_reconciliation WHERE payment_id=(SELECT id FROM payment WHERE order_id=?)", String.class, order.id());
    }

    private String errorCode(Checkout order) {
        return jdbc.queryForObject("SELECT last_error_code FROM payment_reconciliation WHERE payment_id=(SELECT id FROM payment WHERE order_id=?)", String.class, order.id());
    }

    private String paymentStatus(Checkout order) {
        return jdbc.queryForObject("SELECT status FROM payment WHERE order_id=?", String.class, order.id());
    }

    private String orderStatus(Checkout order) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order.id());
    }

    private int refundCount(Checkout order) {
        return count("SELECT COUNT(*) FROM payment_cancel c JOIN payment p ON p.id=c.payment_id WHERE p.order_id=?", order.id());
    }

    private int count(String sql, long id) {
        return jdbc.queryForObject(sql, Integer.class, id);
    }

    private String bearer(long id) {
        return "Bearer " + tokens.encode(new AuthUser(id, AuthUser.SubjectType.MEMBER,
                AuthUser.Role.CUSTOMER, schoolId, null), Instant.now());
    }

    private record Checkout(long id, String paymentId) { }
}
