package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.common.error.BusinessException;
import com.coffeul.order.application.OrderExpiryJob;
import com.coffeul.payment.api.PaymentErrorCode;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@TestPropertySource(properties = "coffeul.payment.gateway=portone")
class PortOnePaymentRetryIntegrationTest extends AbstractIntegrationTest {
    private static final String STORE = "store-retry-test";
    private static final String CHANNEL = "channel-retry-test";
    private static final int AMOUNT = 3000;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtAccessTokenService tokens;
    @Autowired private OrderExpiryJob expiry;
    @MockitoBean private PortOnePaymentClient client;
    private TestFixtures fixtures;
    private long schoolId;
    private long merchantId;
    private long memberId;
    private long storeId;
    private long menuId;
    private String customer;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbc);
        String unique = UUID.randomUUID().toString();
        schoolId = fixtures.createSchool("재시도-" + unique);
        merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & Integer.MAX_VALUE),
                new BigDecimal("0.0300"));
        jdbc.update("""
                INSERT INTO merchant_pg (merchant_id,pg_provider,client_key,secret_ref,portone_store_id)
                VALUES (?,'INICIS_V2',?,'synthetic-server-reference',?)
                """, merchantId, CHANNEL, STORE);
        storeId = fixtures.createStore(merchantId, schoolId, "재시도 매장", "OPEN");
        memberId = fixtures.createMember(schoolId, unique + "@test.ac.kr", "ACTIVE");
        menuId = fixtures.createMenuItem(storeId, "아메리카노", 1500, false);
        customer = bearer(memberId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "READY", "FAILED"})
    void retryReusesStoredIdAndAmountWithoutCreatingPaymentOrExtendingDeadline(String remoteStatus) throws Exception {
        var order = createOrder();
        remote(order, remoteStatus);
        jdbc.update("UPDATE merchant_pg SET client_key='rotated-channel',portone_store_id='rotated-store' WHERE merchant_id=?", merchantId);
        String before = jdbc.queryForObject("SELECT CAST(expires_at AS CHAR) FROM orders WHERE id=?", String.class, order.id());
        for (int i = 0; i < 2; i++) {
            retry(order, customer).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.action").value("OPEN_CHECKOUT"))
                    .andExpect(jsonPath("$.data.payment.attemptNo").value(1))
                    .andExpect(jsonPath("$.data.payment.pgOrderId").value(order.paymentId()))
                    .andExpect(jsonPath("$.data.payment.amount").value(AMOUNT))
                    .andExpect(jsonPath("$.data.payment.orderName").value("아메리카노"))
                    .andExpect(jsonPath("$.data.payment.portOne.storeId").value(STORE))
                    .andExpect(jsonPath("$.data.payment.portOne.channelKey").value(CHANNEL));
        }
        assertThat(countPayments(order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT CAST(expires_at AS CHAR) FROM orders WHERE id=?", String.class, order.id())).isEqualTo(before);
        verify(client, never()).getPaidPayment(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "FAILED"})
    void providerConfirmedFailureCanResumeUnsettledLocalPayment(String localStatus) throws Exception {
        var order = createOrder();
        jdbc.update("UPDATE payment SET status=?,failure_code='OLD',failure_message='synthetic-raw-error' WHERE order_id=?", localStatus, order.id());
        remote(order, "FAILED");
        retry(order, customer).andExpect(status().isOk()).andExpect(jsonPath("$.data.action").value("OPEN_CHECKOUT"));
        assertThat(paymentStatus(order)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT failure_message FROM payment WHERE order_id=?", String.class, order.id())).isNull();
        when(client.getPaidPayment(order.paymentId(), STORE)).thenReturn(paid(order));
        confirm(order).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REQUESTED"));
        assertThat(countPayments(order)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "READY", "PENDING", "PAY_PENDING", "FUTURE_STATUS"})
    void unknownPaymentCannotResumeUntilProviderResolvesIt(String remoteStatus) throws Exception {
        var order = createOrder();
        jdbc.update("UPDATE payment SET status='UNKNOWN' WHERE order_id=?", order.id());
        remote(order, remoteStatus);
        retry(order, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PY012"));
        assertThat(paymentStatus(order)).isEqualTo("UNKNOWN");
        assertThat(countPayments(order)).isEqualTo(1);
    }

    @Test
    void providerPaidPaymentIsPlacedAndNeverReopensCheckout() throws Exception {
        var order = createOrder();
        remote(order, "PAID");
        when(client.getPaidPayment(order.paymentId(), STORE)).thenReturn(paid(order));
        retry(order, customer).andExpect(status().isOk()).andExpect(jsonPath("$.data.action").value("VIEW_ORDER"))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.pickupNo").value(1)).andExpect(jsonPath("$.data.payment").isEmpty());
        retry(order, customer).andExpect(status().isOk()).andExpect(jsonPath("$.data.action").value("VIEW_ORDER"));
        paymentView(order, customer).andExpect(status().isOk()).andExpect(jsonPath("$.data.payment.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.retryAvailable").value(false));
        verify(client, times(1)).getCheckoutState(order.paymentId(), STORE);
        verify(client, times(1)).getPaidPayment(order.paymentId(), STORE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='REQUESTED'", Integer.class, order.id())).isEqualTo(1);
    }

    @Test
    void paymentViewIsReadOnlyAndDoesNotExposeServerContext() throws Exception {
        var order = createOrder();
        jdbc.update("UPDATE payment SET failure_message='synthetic-raw-error' WHERE order_id=?", order.id());
        String body = paymentView(order, customer).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.payment.status").value("READY"))
                .andExpect(jsonPath("$.data.payment.amount").value(AMOUNT))
                .andExpect(jsonPath("$.data.refund").isEmpty())
                .andExpect(jsonPath("$.data.retryAvailable").value(true))
                .andExpect(jsonPath("$.data.payment.channelKey").doesNotExist())
                .andExpect(jsonPath("$.data.payment.paymentKey").doesNotExist())
                .andExpect(jsonPath("$.data.payment.failureMessage").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("synthetic-server-reference", "synthetic-raw-error", CHANNEL);
        verifyNoInteractions(client);
        assertThat(paymentStatus(order)).isEqualTo("READY");
    }

    @Test
    void bothEndpointsRequireCustomerOwnershipAndAuthentication() throws Exception {
        var order = createOrder();
        long other = fixtures.createMember(schoolId, UUID.randomUUID() + "@test.ac.kr", "ACTIVE");
        String owner = "Bearer " + tokens.encode(new AuthUser(1L, AuthUser.SubjectType.STAFF,
                AuthUser.Role.OWNER, null, merchantId), Instant.now());
        for (String authorization : List.of(bearer(other), owner)) {
            int expected = owner.equals(authorization) ? 403 : 404;
            retry(order, authorization).andExpect(status().is(expected));
            paymentView(order, authorization).andExpect(status().is(expected));
        }
        retry(new Checkout(Long.MAX_VALUE, "not-found"), customer).andExpect(status().isNotFound());
        paymentView(new Checkout(Long.MAX_VALUE, "not-found"), customer).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/orders/{id}/payment-attempts", order.id())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders/{id}/payment", order.id())).andExpect(status().isUnauthorized());
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "WITHDRAWN"})
    void inactiveMemberCannotRetryOrReadPayment(String memberStatus) throws Exception {
        var order = createOrder();
        jdbc.update("UPDATE member SET status=? WHERE id=?", memberStatus, memberId);
        retry(order, customer).andExpect(status().isForbidden());
        paymentView(order, customer).andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"paymentId", "store", "channel", "provider", "version", "currency", "amount"})
    void mismatchedProviderContextCannotReopenCheckout(String mismatch) throws Exception {
        var order = createOrder();
        when(client.getCheckoutState(order.paymentId(), STORE)).thenReturn(Optional.of(new PortOnePaymentClient.CheckoutState("FAILED",
                "paymentId".equals(mismatch) ? "wrong" : order.paymentId(), "store".equals(mismatch) ? "wrong" : STORE,
                "channel".equals(mismatch) ? "wrong" : CHANNEL, "provider".equals(mismatch) ? "OTHER" : "INICIS_V2",
                "version".equals(mismatch) ? "V1" : "V2", "currency".equals(mismatch) ? "USD" : "KRW",
                "amount".equals(mismatch) ? AMOUNT + 1 : AMOUNT)));
        retry(order, customer).andExpect(status().is("amount".equals(mismatch) ? 400 : 409))
                .andExpect(jsonPath("$.code").value("amount".equals(mismatch) ? "PY001" : "PY011"));
        assertThat(paymentStatus(order)).isEqualTo("READY");
        assertThat(countPayments(order)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "CANCELLED", "PARTIAL_CANCELLED"})
    void pendingAndCancelledPaymentsCannotReopenCheckout(String remoteStatus) throws Exception {
        var order = createOrder();
        remote(order, remoteStatus);
        retry(order, customer).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PENDING".equals(remoteStatus) ? "PY012" : "PY008"));
    }

    @Test
    void providerFailureNeverProducesCheckoutInstructions() throws Exception {
        var order = createOrder();
        when(client.getCheckoutState(order.paymentId(), STORE)).thenThrow(new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE));
        retry(order, customer).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data").doesNotExist());
        assertThat(countPayments(order)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLOSED", "SOLD_OUT"})
    void unavailableStoreOrMenuDoesNotResumeFailedPayment(String blocked) throws Exception {
        var order = createOrder();
        jdbc.update("UPDATE payment SET status='FAILED' WHERE order_id=?", order.id());
        remote(order, "FAILED");
        if ("CLOSED".equals(blocked)) jdbc.update("UPDATE store SET status='CLOSED' WHERE id=?", storeId);
        else jdbc.update("UPDATE menu_item SET is_sold_out=TRUE WHERE id=?", menuId);
        retry(order, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED".equals(blocked) ? "OD001" : "OD002"));
        assertThat(paymentStatus(order)).isEqualTo("FAILED");
    }

    @Test
    void concurrentRetriesReturnOneLogicalPaymentWithoutHoldingTransactionDuringLookup() throws Exception {
        var order = createOrder();
        var both = new CountDownLatch(2);
        when(client.getCheckoutState(order.paymentId(), STORE)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            both.countDown();
            assertThat(both.await(5, TimeUnit.SECONDS)).isTrue();
            return Optional.of(state(order, "FAILED"));
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> retry(order, customer).andReturn().getResponse());
            var b = pool.submit(() -> retry(order, customer).andReturn().getResponse());
            for (var response : List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))) {
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(mapper.readTree(response.getContentAsString()).at("/data/payment/pgOrderId").asText()).isEqualTo(order.paymentId());
            }
        }
        assertThat(countPayments(order)).isEqualTo(1);
    }

    @Test
    void confirmationDuringLookupReturnsOrderInsteadOfStaleCheckout() throws Exception {
        var order = createOrder();
        when(client.getPaidPayment(order.paymentId(), STORE)).thenReturn(paid(order));
        when(client.getCheckoutState(order.paymentId(), STORE)).thenAnswer(call -> {
            confirm(order).andExpect(status().isOk());
            return Optional.of(state(order, "FAILED"));
        });
        retry(order, customer).andExpect(status().isOk()).andExpect(jsonPath("$.data.action").value("VIEW_ORDER"))
                .andExpect(jsonPath("$.data.payment").isEmpty());
        assertThat(countPayments(order)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "PAID"})
    void expiryDuringLookupCannotReopenCheckoutAndPaidCaseQueuesRefund(String remoteStatus) throws Exception {
        var order = createOrder();
        when(client.getPaidPayment(order.paymentId(), STORE)).thenReturn(paid(order));
        when(client.getCheckoutState(order.paymentId(), STORE)).thenAnswer(call -> {
            fixtures.ageOrderExpiresAt(order.id(), Instant.now().minusSeconds(2));
            assertThat(expiry.expireOne(order.id(), Instant.now())).isTrue();
            return Optional.of(state(order, remoteStatus));
        });
        retry(order, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OD009"));
        paymentView(order, customer).andExpect(status().isOk()).andExpect(jsonPath("$.data.orderStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.data.retryAvailable").value(false));
        int refunds = jdbc.queryForObject("SELECT COUNT(*) FROM payment_cancel c JOIN payment p ON p.id=c.payment_id WHERE p.order_id=?", Integer.class, order.id());
        assertThat(refunds).isEqualTo("PAID".equals(remoteStatus) ? 1 : 0);
        assertThat(jdbc.queryForObject("SELECT pickup_no FROM orders WHERE id=?", Integer.class, order.id())).isNull();
    }

    @Test
    void expiredOrderDoesNotCallProviderAndCannotExtendWindow() throws Exception {
        var order = createOrder();
        fixtures.ageOrderExpiresAt(order.id(), Instant.now().minusSeconds(2));
        retry(order, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OD009"));
        verifyNoInteractions(client);
    }

    @Test
    void deactivationDuringLookupBlocksCheckoutResponse() throws Exception {
        var order = createOrder();
        when(client.getCheckoutState(order.paymentId(), STORE)).thenAnswer(call -> {
            jdbc.update("UPDATE member SET status='SUSPENDED' WHERE id=?", memberId);
            return Optional.of(state(order, "FAILED"));
        });
        retry(order, customer).andExpect(status().isForbidden());
    }

    @Test
    void clientSuppliedStatusAndAmountCannotOverrideProvider() throws Exception {
        var order = createOrder();
        remote(order, "PENDING");
        mvc.perform(post("/api/v1/orders/{id}/payment-attempts", order.id()).header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FAILED\",\"amount\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PY012"));
        assertThat(paymentStatus(order)).isEqualTo("READY");
    }

    private Checkout createOrder() throws Exception {
        String body = mvc.perform(post("/api/v1/orders").header("Authorization", customer)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("storeId", storeId, "withdrawalLimitAgreed", true,
                                "items", List.of(Map.of("menuId", menuId, "quantity", 2))))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var data = mapper.readTree(body).path("data");
        return new Checkout(data.path("orderId").asLong(), data.at("/payment/portOne/paymentId").asText());
    }

    private ResultActions retry(Checkout order, String authorization) throws Exception {
        return mvc.perform(post("/api/v1/orders/{id}/payment-attempts", order.id()).header("Authorization", authorization));
    }

    private ResultActions paymentView(Checkout order, String authorization) throws Exception {
        return mvc.perform(get("/api/v1/orders/{id}/payment", order.id()).header("Authorization", authorization));
    }

    private ResultActions confirm(Checkout order) throws Exception {
        return mvc.perform(post("/api/v1/payments/confirm").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("orderId", order.id(), "paymentId", order.paymentId()))));
    }

    private void remote(Checkout order, String remoteStatus) {
        when(client.getCheckoutState(order.paymentId(), STORE)).thenReturn("MISSING".equals(remoteStatus)
                ? Optional.empty() : Optional.of(state(order, remoteStatus)));
    }

    private PortOnePaymentClient.CheckoutState state(Checkout order, String status) {
        return new PortOnePaymentClient.CheckoutState(status, order.paymentId(), STORE, CHANNEL, "INICIS_V2", "V2", "KRW", AMOUNT);
    }

    private PortOnePaymentClient.PaidPayment paid(Checkout order) {
        return new PortOnePaymentClient.PaidPayment(order.paymentId(), "transaction-" + order.id(), STORE,
                CHANNEL, "INICIS_V2", "V2", "KRW", AMOUNT, "CARD", Instant.now());
    }

    private int countPayments(Checkout order) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE order_id=?", Integer.class, order.id());
    }

    private String paymentStatus(Checkout order) {
        return jdbc.queryForObject("SELECT status FROM payment WHERE order_id=?", String.class, order.id());
    }

    private String bearer(long id) {
        return "Bearer " + tokens.encode(new AuthUser(id, AuthUser.SubjectType.MEMBER,
                AuthUser.Role.CUSTOMER, schoolId, null), Instant.now());
    }

    private record Checkout(long id, String paymentId) { }
}
