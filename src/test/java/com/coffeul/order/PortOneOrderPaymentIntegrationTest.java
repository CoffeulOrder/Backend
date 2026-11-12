package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.common.error.BusinessException;
import com.coffeul.order.application.OrderExpiryJob;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.infrastructure.PortOnePaymentClient;
import com.coffeul.payment.infrastructure.PortOnePaymentClient.PaidPayment;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@TestPropertySource(properties = {"coffeul.payment.gateway=portone", "coffeul.dev.payment-flow-enabled=true",
        "coffeul.payment.portone.webhook-enabled=true",
        "coffeul.payment.portone.webhook-secret=whsec_" + PortOneOrderPaymentIntegrationTest.TEST_SIGNING_KEY})
class PortOneOrderPaymentIntegrationTest extends AbstractIntegrationTest {
    static final String TEST_SIGNING_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String PORTONE_STORE = "store-test-only";
    private static final String CHANNEL = "channel-key-test-only";
    private static final int AMOUNT = 4500;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtAccessTokenService tokens;
    @Autowired private OrderExpiryJob expiryJob;
    @MockitoBean private PortOnePaymentClient portOne;

    private TestFixtures fixtures;
    private long schoolId;
    private long merchantId;
    private long storeId;
    private long menuId;
    private long memberId;
    private String customer;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbc);
        String unique = UUID.randomUUID().toString();
        schoolId = fixtures.createSchool("포트원-" + unique);
        merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & 0x7fffffff),
                new BigDecimal("0.0300"));
        jdbc.update("""
                INSERT INTO merchant_pg (merchant_id, pg_provider, client_key, secret_ref, portone_store_id)
                VALUES (?, 'INICIS_V2', ?, 'test-fixture', ?)
                """, merchantId, CHANNEL, PORTONE_STORE);
        storeId = fixtures.createStore(merchantId, schoolId, "포트원 테스트 매장", "OPEN");
        menuId = fixtures.createMenuItem(storeId, "라떼", AMOUNT, false);
        memberId = fixtures.createMember(schoolId, unique + "@test.ac.kr", "ACTIVE");
        customer = bearer(memberId);
    }

    @Test
    void orderReplayUsesSavedChannelAfterMerchantConfigurationChanges() throws Exception {
        String key = UUID.randomUUID().toString();
        var order = create(key, 201);
        jdbc.update("UPDATE merchant_pg SET client_key = ?, portone_store_id = ? WHERE merchant_id = ?",
                "channel-key-rotated", "store-rotated", merchantId);
        assertThat(create(key, 200)).isEqualTo(order);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE order_id = ?", Integer.class, order.id()))
                .isEqualTo(1);
        when(portOne.getPaidPayment(order.paymentId(), PORTONE_STORE)).thenReturn(paid(order, AMOUNT, CHANNEL));
        confirm(order, customer).andExpect(status().isOk()).andExpect(jsonPath("$.data.pickupNo").value(1));
        confirm(order, customer).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REQUESTED"));
        verify(portOne, times(1)).getPaidPayment(order.paymentId(), PORTONE_STORE);
        assertApprovedOnce(order);
    }

    @Test
    void missingMerchantConfigurationRollsBackOrder() throws Exception {
        jdbc.update("UPDATE merchant_pg SET portone_store_id = NULL WHERE merchant_id = ?", merchantId);
        submit(UUID.randomUUID().toString()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PY006"));
        assertThat(fixtures.countOrdersByMember(memberId)).isZero();
        verifyNoInteractions(portOne);
    }

    @Test
    void otherCustomerAndWrongPaymentIdCannotTriggerProviderLookup() throws Exception {
        var order = create(UUID.randomUUID().toString(), 201);
        long other = fixtures.createMember(schoolId, UUID.randomUUID() + "@test.ac.kr", "ACTIVE");
        confirm(order, bearer(other)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OD005"));
        confirm(new Checkout(order.id(), "wrong-payment"), customer).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PY005"));
        mvc.perform(post("/api/v1/dev/orders/{id}/confirm-payment", order.id()).header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(portOne);
        assertPending(order);
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "channel", "store", "paymentId", "currency"})
    void mismatchedProviderPaymentCannotPlaceOrder(String mismatch) throws Exception {
        var order = create(UUID.randomUUID().toString(), 201);
        var payment = new PaidPayment("paymentId".equals(mismatch) ? "wrong-payment" : order.paymentId(),
                "transaction-test-" + order.id(), "store".equals(mismatch) ? "store-other" : PORTONE_STORE,
                "channel".equals(mismatch) ? "channel-key-other" : CHANNEL, "INICIS_V2", "V2",
                "currency".equals(mismatch) ? "USD" : "KRW", "amount".equals(mismatch) ? AMOUNT + 1 : AMOUNT,
                "CARD", Instant.now());
        when(portOne.getPaidPayment(order.paymentId(), PORTONE_STORE)).thenReturn(payment);
        confirm(order, customer).andExpect(status().is("amount".equals(mismatch) ? 400 : 409))
                .andExpect(jsonPath("$.code").value("amount".equals(mismatch) ? "PY001" : "PY011"));
        assertPending(order);
    }

    @Test
    void concurrentConfirmationIssuesOnePickupNumberAndOneHistoryEntry() throws Exception {
        var order = create(UUID.randomUUID().toString(), 201);
        var bothLookups = new CountDownLatch(2);
        when(portOne.getPaidPayment(order.paymentId(), PORTONE_STORE)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            bothLookups.countDown();
            assertThat(bothLookups.await(5, TimeUnit.SECONDS)).isTrue();
            return paid(order, AMOUNT, CHANNEL);
        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> confirm(order, customer).andReturn());
            var second = workers.submit(() -> confirm(order, customer).andReturn());
            for (var result : List.of(first, second)) {
                var response = result.get(10, TimeUnit.SECONDS).getResponse();
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(mapper.readTree(response.getContentAsString()).path("data").path("pickupNo").asInt())
                        .isEqualTo(1);
            }
        }
        assertApprovedOnce(order);
    }

    @Test
    void webhookConfirmsWithoutCustomerCallbackAndRetriesDoNotRepeatLookup() throws Exception {
        var order = create(UUID.randomUUID().toString(), 201);
        when(portOne.getPaidPayment(order.paymentId(), PORTONE_STORE)).thenReturn(paid(order, AMOUNT, CHANNEL));
        String body = webhookBody("Transaction.Paid", order.paymentId(), PORTONE_STORE);
        webhook(body).andExpect(status().isNoContent());
        webhook(body).andExpect(status().isNoContent());
        confirm(order, customer).andExpect(status().isOk()).andExpect(jsonPath("$.data.pickupNo").value(1));
        verify(portOne, times(1)).getPaidPayment(order.paymentId(), PORTONE_STORE);
        assertApprovedOnce(order);
    }

    @Test
    void webhookAndAppConfirmationRaceIssuesOnePickupNumber() throws Exception {
        var order = create(UUID.randomUUID().toString(), 201);
        var bothLookups = new CountDownLatch(2);
        when(portOne.getPaidPayment(order.paymentId(), PORTONE_STORE)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            bothLookups.countDown();
            assertThat(bothLookups.await(5, TimeUnit.SECONDS)).isTrue();
            return paid(order, AMOUNT, CHANNEL);
        });
        String body = webhookBody("Transaction.Paid", order.paymentId(), PORTONE_STORE);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var webhookResult = workers.submit(() -> webhook(body).andReturn().getResponse().getStatus());
            var appResult = workers.submit(() -> confirm(order, customer).andReturn().getResponse());
            assertThat(webhookResult.get(10, TimeUnit.SECONDS)).isEqualTo(204);
            var appResponse = appResult.get(10, TimeUnit.SECONDS);
            assertThat(appResponse.getStatus()).isEqualTo(200);
            assertThat(mapper.readTree(appResponse.getContentAsString()).at("/data/pickupNo").asInt()).isEqualTo(1);
        }
        assertApprovedOnce(order);
        assertThat(jdbc.queryForObject("SELECT last_pickup_no FROM order_daily_counter WHERE store_id = ?",
                Integer.class, storeId)).isEqualTo(1);
    }

    @Test
    void tamperedAndStaleWebhooksCannotQueryProvider() throws Exception {
        var order = create(UUID.randomUUID().toString(), 201);
        String body = webhookBody("Transaction.Paid", order.paymentId(), PORTONE_STORE);
        long now = Instant.now().getEpochSecond();
        webhook(body + " ", now, sign(body, now)).andExpect(status().isUnauthorized());
        long stale = now - 301;
        webhook(body, stale, sign(body, stale)).andExpect(status().isUnauthorized());
        verifyNoInteractions(portOne);
        assertPending(order);
    }

    @Test
    void unrelatedEventsAreIgnoredAndWrongStoreCannotConfirm() throws Exception {
        var order = create(UUID.randomUUID().toString(), 201);
        webhook(webhookBody("Transaction.Cancelled", order.paymentId(), PORTONE_STORE))
                .andExpect(status().isNoContent());
        webhook(webhookBody("Transaction.Paid", "unrelated-payment", PORTONE_STORE))
                .andExpect(status().isNoContent());
        webhook(webhookBody("Transaction.Paid", order.paymentId(), "store-other"))
                .andExpect(status().isConflict());
        verifyNoInteractions(portOne);
        assertPending(order);
    }

    @Test
    void webhookRetryCanRecoverFromTemporaryProviderFailure() throws Exception {
        var order = create(UUID.randomUUID().toString(), 201);
        when(portOne.getPaidPayment(order.paymentId(), PORTONE_STORE))
                .thenThrow(new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE))
                .thenReturn(paid(order, AMOUNT, CHANNEL));
        String body = webhookBody("Transaction.Paid", order.paymentId(), PORTONE_STORE);
        webhook(body).andExpect(status().isServiceUnavailable());
        assertPending(order);
        webhook(body).andExpect(status().isNoContent());
        assertApprovedOnce(order);
    }

    @Test
    void expiryDuringProviderLookupCannotResurrectOrder() throws Exception {
        var order = create(UUID.randomUUID().toString(), 201);
        when(portOne.getPaidPayment(order.paymentId(), PORTONE_STORE)).thenAnswer(invocation -> {
            fixtures.ageOrderExpiresAt(order.id(), Instant.now().minusSeconds(2));
            assertThat(expiryJob.expireOne(order.id(), Instant.now())).isTrue();
            return paid(order, AMOUNT, CHANNEL);
        });
        confirm(order, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OD009"));
        assertThat(orderStatus(order)).isEqualTo("EXPIRED");
        assertThat(paymentStatus(order)).isEqualTo("READY");
        confirm(order, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OD009"));
        verify(portOne, times(1)).getPaidPayment(order.paymentId(), PORTONE_STORE);
    }

    @Test
    void pickupFailureRollsBackLocalApprovalAndSamePaymentCanBeConfirmedAgain() throws Exception {
        var order = create(UUID.randomUUID().toString(), 201);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        jdbc.update("INSERT INTO order_daily_counter (store_id,business_date,last_pickup_no) VALUES (?,?,?)",
                storeId, today, Short.MAX_VALUE);
        when(portOne.getPaidPayment(order.paymentId(), PORTONE_STORE)).thenReturn(paid(order, AMOUNT, CHANNEL));
        confirm(order, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("C007"));
        assertPending(order);
        jdbc.update("DELETE FROM order_daily_counter WHERE store_id=? AND business_date=?", storeId, today);
        confirm(order, customer).andExpect(status().isOk()).andExpect(jsonPath("$.data.pickupNo").value(1));
        assertApprovedOnce(order);
    }

    private Checkout create(String key, int expectedStatus) throws Exception {
        var response = submit(key).andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.data.payment.portOne.storeId").value(PORTONE_STORE))
                .andExpect(jsonPath("$.data.payment.portOne.channelKey").value(CHANNEL))
                .andExpect(jsonPath("$.data.payment.portOne.currency").value("CURRENCY_KRW"))
                .andExpect(jsonPath("$.data.payment.amount").value(AMOUNT)).andReturn().getResponse();
        JsonNode data = mapper.readTree(response.getContentAsString()).path("data");
        return new Checkout(data.path("orderId").asLong(), data.path("payment").path("portOne").path("paymentId").asText());
    }

    private ResultActions submit(String key) throws Exception {
        return mvc.perform(post("/api/v1/orders").header("Authorization", customer).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                        "storeId", storeId, "items", List.of(Map.of("menuId", menuId, "quantity", 1)),
                        "withdrawalLimitAgreed", true))));
    }

    private ResultActions confirm(Checkout order, String bearer) throws Exception {
        return mvc.perform(post("/api/v1/payments/confirm").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                        "orderId", order.id(), "paymentId", order.paymentId()))));
    }

    private String webhookBody(String type, String paymentId, String store) throws Exception {
        return mapper.writeValueAsString(Map.of("type", type, "data", Map.of("paymentId", paymentId, "storeId", store)));
    }

    private ResultActions webhook(String body) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        return webhook(body, timestamp, sign(body, timestamp));
    }

    private ResultActions webhook(String body, long timestamp, String signature) throws Exception {
        return mvc.perform(post("/api/v1/payments/webhooks/portone").contentType(MediaType.APPLICATION_JSON)
                .header("webhook-id", "webhook-test-id").header("webhook-timestamp", timestamp)
                .header("webhook-signature", signature).content(body));
    }

    private String sign(String body, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(TEST_SIGNING_KEY), "HmacSHA256"));
        byte[] signed = mac.doFinal(("webhook-test-id." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(signed);
    }

    private PaidPayment paid(Checkout order, long amount, String channel) {
        return new PaidPayment(order.paymentId(), "transaction-test-" + order.id(), PORTONE_STORE,
                channel, "INICIS_V2", "V2", "KRW", amount, "CARD", Instant.now());
    }

    private String bearer(long id) {
        return "Bearer " + tokens.encode(new AuthUser(id, AuthUser.SubjectType.MEMBER,
                AuthUser.Role.CUSTOMER, schoolId, null), Instant.now());
    }

    private void assertApprovedOnce(Checkout order) {
        assertThat(paymentStatus(order)).isEqualTo("APPROVED");
        assertThat(orderStatus(order)).isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id = ? AND to_status = 'REQUESTED'",
                Integer.class, order.id())).isEqualTo(1);
    }

    private void assertPending(Checkout order) {
        assertThat(paymentStatus(order)).isEqualTo("READY");
        assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
    }

    private String paymentStatus(Checkout order) {
        return jdbc.queryForObject("SELECT status FROM payment WHERE order_id = ?", String.class, order.id());
    }

    private String orderStatus(Checkout order) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order.id());
    }

    private record Checkout(long id, String paymentId) { }
}
