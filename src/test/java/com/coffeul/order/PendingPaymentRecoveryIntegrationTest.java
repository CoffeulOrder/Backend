package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.common.error.BusinessException;
import com.coffeul.order.application.OrderExpiryJob;
import com.coffeul.order.application.PaymentReconciliationJob;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.api.PaymentRecoveryApi;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@TestPropertySource(properties = {"coffeul.payment.gateway=portone", "coffeul.payment.recovery.batch-size=100",
        "coffeul.payment.recovery.pending-grace-seconds=60", "coffeul.payment.portone.webhook-enabled=true",
        "coffeul.payment.portone.webhook-secret=whsec_" + PortOneOrderPaymentIntegrationTest.TEST_SIGNING_KEY})
class PendingPaymentRecoveryIntegrationTest extends AbstractIntegrationTest {
    private static final String STORE = "store-pending-recovery-test";
    private static final String CHANNEL = "channel-pending-recovery-test";
    private static final int AMOUNT = 3000;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtAccessTokenService tokens;
    @Autowired private PaymentReconciliationJob job;
    @Autowired private PaymentRecoveryApi recovery;
    @Autowired private TransactionTemplate transaction;
    @Autowired private OrderExpiryJob expiry;
    @MockitoBean private PortOnePaymentClient client;
    private TestFixtures fixtures;
    private long schoolId;
    private long memberId;
    private long storeId;
    private long menuId;
    private String customer;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        transaction.executeWithoutResult(status -> recovery.discover(now, now.plusSeconds(3600), 10000));
        jdbc.update("UPDATE payment_reconciliation SET next_retry_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY) WHERE status='WAITING'");
        fixtures = new TestFixtures(jdbc);
        String unique = UUID.randomUUID().toString();
        schoolId = fixtures.createSchool("기한 전 누락 복구-" + unique);
        long merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & Integer.MAX_VALUE), new BigDecimal("0.0300"));
        jdbc.update("INSERT INTO merchant_pg (merchant_id,pg_provider,client_key,secret_ref,portone_store_id) "
                + "VALUES (?,'INICIS_V2',?,'synthetic-fixture',?)", merchantId, CHANNEL, STORE);
        storeId = fixtures.createStore(merchantId, schoolId, "기한 전 복구 매장", "OPEN");
        memberId = fixtures.createMember(schoolId, unique + "@test.ac.kr", "ACTIVE");
        menuId = fixtures.createMenuItem(storeId, "아메리카노", AMOUNT, false);
        customer = "Bearer " + tokens.encode(new AuthUser(memberId, AuthUser.SubjectType.MEMBER,
                AuthUser.Role.CUSTOMER, schoolId, null), Instant.now());
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY", "UNKNOWN"})
    void paidPaymentIsPlacedBeforeExpiryWithoutAppOrWebhook(String localStatus) throws Exception {
        var order = dueOrder();
        jdbc.update("UPDATE payment SET status=? WHERE order_id=?", localStatus, order.id());
        when(client.getPaidPayment(order.paymentId(), STORE)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return payment(order, AMOUNT, CHANNEL);
        });
        assertThat(job.processBatch()).isEqualTo(1);
        assertPlacedOnce(order);
        assertThat(recoveryStatus(order)).isEqualTo("COMPLETE");
        assertThat(job.processBatch()).isZero();
        verify(client, times(1)).getPaidPayment(order.paymentId(), STORE);
    }

    @Test
    void newlyCreatedCheckoutIsNotDiscoveredDuringGracePeriod() throws Exception {
        var order = createOrder();
        assertThat(job.processBatch()).isZero();
        assertThat(count("SELECT COUNT(*) FROM payment_reconciliation WHERE payment_id=(SELECT id FROM payment WHERE order_id=?)", order.id())).isZero();
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "EXPIRED"})
    void pendingRetryWaitIsBoundedWhileExpiredRetryBackoffIsPreserved(String state) throws Exception {
        var order = dueOrder();
        if ("EXPIRED".equals(state)) {
            fixtures.ageOrderExpiresAt(order.id(), Instant.now().minusSeconds(2));
            assertThat(expiry.expireOne(order.id(), Instant.now())).isTrue();
        }
        when(client.getPaidPayment(order.paymentId(), STORE)).thenThrow(new BusinessException(PaymentErrorCode.NOT_PAID));
        job.processBatch();
        dueNow(order);
        job.processBatch();
        assertThat(recoveryStatus(order)).isEqualTo("WAITING");
        assertThat(errorCode(order)).isEqualTo("PY009");
        long waitSeconds = jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(6),next_retry_at) "
                + "FROM payment_reconciliation WHERE payment_id=(SELECT id FROM payment WHERE order_id=?)", Long.class, order.id());
        if ("PENDING_PAYMENT".equals(state)) assertThat(waitSeconds).isBetween(40L, 65L);
        else assertThat(waitSeconds).isBetween(100L, 125L);
        assertThat(orderStatus(order)).isEqualTo(state);
        assertThat(paymentStatus(order)).isEqualTo("READY");
        assertThat(refundCount(order)).isZero();
    }

    @Test
    void temporaryLookupFailureCanBeRecoveredBeforeExpiry() throws Exception {
        var order = dueOrder();
        when(client.getPaidPayment(order.paymentId(), STORE))
                .thenThrow(new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE))
                .thenReturn(payment(order, AMOUNT, CHANNEL));
        job.processBatch();
        assertThat(recoveryStatus(order)).isEqualTo("WAITING");
        assertThat(errorCode(order)).isEqualTo("PY010");
        assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        dueNow(order);
        job.processBatch();
        assertPlacedOnce(order);
        assertThat(recoveryStatus(order)).isEqualTo("COMPLETE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "channel"})
    void providerMismatchBlocksPlacementAndRefund(String mismatch) throws Exception {
        var order = dueOrder();
        when(client.getPaidPayment(order.paymentId(), STORE)).thenReturn(payment(order,
                "amount".equals(mismatch) ? AMOUNT + 1 : AMOUNT, "channel".equals(mismatch) ? "wrong" : CHANNEL));
        job.processBatch();
        assertThat(recoveryStatus(order)).isEqualTo("FAILED");
        assertThat(errorCode(order)).isEqualTo("amount".equals(mismatch) ? "PY001" : "PY011");
        assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(paymentStatus(order)).isEqualTo("READY");
        assertThat(refundCount(order)).isZero();
    }

    @Test
    void callbackThatAlreadyPlacedOrderCompletesQueuedWorkWithoutAnotherLookup() throws Exception {
        var order = dueOrder();
        discover(order);
        when(client.getPaidPayment(order.paymentId(), STORE)).thenReturn(payment(order, AMOUNT, CHANNEL));
        confirm(order);
        assertThat(job.processBatch()).isEqualTo(1);
        assertPlacedOnce(order);
        assertThat(recoveryStatus(order)).isEqualTo("COMPLETE");
        verify(client, times(1)).getPaidPayment(order.paymentId(), STORE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLOSED", "INACTIVE"})
    void recoveredPaymentThatCannotBePlacedQueuesCompensation(String blocked) throws Exception {
        var order = dueOrder();
        if ("CLOSED".equals(blocked)) jdbc.update("UPDATE store SET status='CLOSED' WHERE id=?", storeId);
        else jdbc.update("UPDATE member SET status='SUSPENDED' WHERE id=?", memberId);
        when(client.getPaidPayment(order.paymentId(), STORE)).thenReturn(payment(order, AMOUNT, CHANNEL));
        job.processBatch();
        assertThat(orderStatus(order)).isEqualTo("REJECTED");
        assertThat(refundCount(order)).isEqualTo(1);
        assertThat(recoveryStatus(order)).isEqualTo("COMPLETE");
        assertThat(count("SELECT COUNT(*) FROM orders WHERE id=? AND pickup_no IS NOT NULL", order.id())).isZero();
    }

    @Test
    void expiryDuringLookupUsesRefundPathInsteadOfResurrectingOrder() throws Exception {
        var order = dueOrder();
        when(client.getPaidPayment(order.paymentId(), STORE)).thenAnswer(call -> {
            fixtures.ageOrderExpiresAt(order.id(), Instant.now().minusSeconds(2));
            assertThat(expiry.expireOne(order.id(), Instant.now())).isTrue();
            return payment(order, AMOUNT, CHANNEL);
        });
        job.processBatch();
        assertThat(orderStatus(order)).isEqualTo("EXPIRED");
        assertThat(refundCount(order)).isEqualTo(1);
        assertThat(recoveryStatus(order)).isEqualTo("COMPLETE");
        assertThat(count("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='REQUESTED'", order.id())).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"app", "webhook"})
    void recoveryAndCallbackRaceIssueOnePickupNumber(String callback) throws Exception {
        var order = dueOrder();
        var both = new CountDownLatch(2);
        when(client.getPaidPayment(order.paymentId(), STORE)).thenAnswer(call -> {
            both.countDown();
            assertThat(both.await(5, TimeUnit.SECONDS)).isTrue();
            return payment(order, AMOUNT, CHANNEL);
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var worker = pool.submit(job::processBatch);
            var notification = pool.submit(() -> {
                if ("app".equals(callback)) confirm(order);
                else webhook(order);
                return true;
            });
            assertThat(worker.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(notification.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertPlacedOnce(order);
        assertThat(recoveryStatus(order)).isEqualTo("COMPLETE");
    }

    @Test
    void unpaidOrderCanReopenSameCheckoutAndStillBeRecoveredLater() throws Exception {
        var order = dueOrder();
        String deadline = jdbc.queryForObject("SELECT CAST(expires_at AS CHAR) FROM orders WHERE id=?", String.class, order.id());
        when(client.getPaidPayment(order.paymentId(), STORE))
                .thenThrow(new BusinessException(PaymentErrorCode.NOT_PAID)).thenReturn(payment(order, AMOUNT, CHANNEL));
        job.processBatch();
        when(client.getCheckoutState(order.paymentId(), STORE)).thenReturn(Optional.of(new PortOnePaymentClient.CheckoutState(
                "FAILED", order.paymentId(), STORE, CHANNEL, "INICIS_V2", "V2", "KRW", AMOUNT)));
        mvc.perform(post("/api/v1/orders/{id}/payment-attempts", order.id()).header("Authorization", customer))
                .andExpect(status().isOk());
        assertThat(recoveryStatus(order)).isEqualTo("WAITING");
        dueNow(order);
        job.processBatch();
        assertPlacedOnce(order);
        assertThat(jdbc.queryForObject("SELECT CAST(expires_at AS CHAR) FROM orders WHERE id=?", String.class, order.id())).isEqualTo(deadline);
        assertThat(count("SELECT COUNT(*) FROM payment WHERE order_id=?", order.id())).isEqualTo(1);
    }

    @Test
    void transactionFailureKeepsWorkRetryableAndDoesNotPartiallyApprove() throws Exception {
        var order = dueOrder();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        jdbc.update("INSERT INTO order_daily_counter (store_id,business_date,last_pickup_no) VALUES (?,?,?)", storeId, today, Short.MAX_VALUE);
        when(client.getPaidPayment(order.paymentId(), STORE)).thenReturn(payment(order, AMOUNT, CHANNEL));
        job.processBatch();
        assertThat(recoveryStatus(order)).isEqualTo("WAITING");
        assertThat(errorCode(order)).isEqualTo("C007");
        assertThat(paymentStatus(order)).isEqualTo("READY");
        assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        jdbc.update("DELETE FROM order_daily_counter WHERE store_id=? AND business_date=?", storeId, today);
        dueNow(order);
        job.processBatch();
        assertPlacedOnce(order);
        assertThat(recoveryStatus(order)).isEqualTo("COMPLETE");
    }

    private Checkout dueOrder() throws Exception {
        var order = createOrder();
        jdbc.update("UPDATE payment SET created_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 MINUTE) WHERE order_id=?", order.id());
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

    private void confirm(Checkout order) throws Exception {
        mvc.perform(post("/api/v1/payments/confirm").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("orderId", order.id(), "paymentId", order.paymentId()))))
                .andExpect(status().isOk());
    }

    private void webhook(Checkout order) throws Exception {
        String body = mapper.writeValueAsString(Map.of("type", "Transaction.Paid", "data", Map.of("paymentId", order.paymentId(), "storeId", STORE)));
        String id = "pending-recovery-webhook";
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(PortOneOrderPaymentIntegrationTest.TEST_SIGNING_KEY), "HmacSHA256"));
        String signature = "v1," + Base64.getEncoder().encodeToString(mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        mvc.perform(post("/api/v1/payments/webhooks/portone").contentType(MediaType.APPLICATION_JSON)
                        .header("webhook-id", id).header("webhook-timestamp", timestamp).header("webhook-signature", signature).content(body))
                .andExpect(status().isNoContent());
    }

    private PortOnePaymentClient.PaidPayment payment(Checkout order, int amount, String channel) {
        return new PortOnePaymentClient.PaidPayment(order.paymentId(), "transaction-" + order.id(), STORE,
                channel, "INICIS_V2", "V2", "KRW", amount, "CARD", Instant.now());
    }

    private void assertPlacedOnce(Checkout order) {
        assertThat(orderStatus(order)).isEqualTo("REQUESTED");
        assertThat(paymentStatus(order)).isEqualTo("APPROVED");
        assertThat(count("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='REQUESTED'", order.id())).isEqualTo(1);
        assertThat(count("SELECT last_pickup_no FROM order_daily_counter WHERE store_id=?", storeId)).isEqualTo(1);
        assertThat(refundCount(order)).isZero();
    }

    private void discover(Checkout order) {
        Instant now = Instant.now();
        transaction.executeWithoutResult(status -> recovery.discover(now, now.minusSeconds(60), 100));
    }

    private void dueNow(Checkout order) {
        jdbc.update("UPDATE payment_reconciliation SET next_retry_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE payment_id=(SELECT id FROM payment WHERE order_id=?)", order.id());
    }

    private String orderStatus(Checkout order) { return jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order.id()); }
    private String paymentStatus(Checkout order) { return jdbc.queryForObject("SELECT status FROM payment WHERE order_id=?", String.class, order.id()); }
    private String recoveryStatus(Checkout order) { return jdbc.queryForObject("SELECT status FROM payment_reconciliation WHERE payment_id=(SELECT id FROM payment WHERE order_id=?)", String.class, order.id()); }
    private String errorCode(Checkout order) { return jdbc.queryForObject("SELECT last_error_code FROM payment_reconciliation WHERE payment_id=(SELECT id FROM payment WHERE order_id=?)", String.class, order.id()); }
    private int refundCount(Checkout order) { return count("SELECT COUNT(*) FROM payment_cancel c JOIN payment p ON p.id=c.payment_id WHERE p.order_id=?", order.id()); }
    private int count(String sql, long id) { return jdbc.queryForObject(sql, Integer.class, id); }
    private record Checkout(long id, String paymentId) { }
}
