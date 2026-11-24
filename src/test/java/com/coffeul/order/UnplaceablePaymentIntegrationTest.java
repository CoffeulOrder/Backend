package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.common.error.BusinessException;
import com.coffeul.menu.api.MenuQueryApi;
import com.coffeul.payment.api.PaymentCancellationApi;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.application.PaymentCancellationProcessor;
import com.coffeul.payment.infrastructure.PortOnePaymentClient;
import com.coffeul.store.api.StoreQueryApi;
import com.coffeul.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@TestPropertySource(properties = {"coffeul.payment.gateway=portone", "coffeul.payment.cancel.batch-size=100",
        "coffeul.payment.portone.webhook-enabled=true",
        "coffeul.payment.portone.webhook-secret=whsec_" + PortOneOrderPaymentIntegrationTest.TEST_SIGNING_KEY})
class UnplaceablePaymentIntegrationTest extends AbstractIntegrationTest {
    private static final String STORE = "store-compensation-test";
    private static final String CHANNEL = "channel-compensation-test";
    private static final int AMOUNT = 3000;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtAccessTokenService tokens;
    @Autowired private PaymentCancellationProcessor refunds;
    @MockitoBean private PortOnePaymentClient client;
    @MockitoSpyBean private PaymentCancellationApi cancellations;
    @MockitoSpyBean private StoreQueryApi stores;
    @MockitoSpyBean private MenuQueryApi menus;
    private TestFixtures fixtures;
    private long schoolId;
    private long merchantId;
    private long memberId;
    private long storeId;
    private long menuId;
    private long optionId;
    private String customer;
    private String owner;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbc);
        String unique = UUID.randomUUID().toString();
        schoolId = fixtures.createSchool("접수 불가-" + unique);
        merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & Integer.MAX_VALUE),
                new BigDecimal("0.0300"));
        jdbc.update("""
                INSERT INTO merchant_pg (merchant_id,pg_provider,client_key,secret_ref,portone_store_id)
                VALUES (?,'INICIS_V2',?,'synthetic-fixture',?)
                """, merchantId, CHANNEL, STORE);
        storeId = fixtures.createStore(merchantId, schoolId, "접수 불가 테스트 매장", "OPEN");
        memberId = fixtures.createMember(schoolId, unique + "@test.ac.kr", "ACTIVE");
        menuId = fixtures.createMenuItem(storeId, "카페라떼", AMOUNT, false);
        long group = fixtures.createOptionGroup(menuId, "사이즈", "SIZE", true, 1, 1);
        optionId = fixtures.createOptionItem(group, "기본", 0, true);
        customer = bearer(memberId);
        long staffId = fixtures.createStaffAccount(merchantId, "OWNER", "owner-" + unique,
                "password1234", "테스트 사장님", "ACTIVE");
        owner = "Bearer " + tokens.encode(new AuthUser(staffId, AuthUser.SubjectType.STAFF,
                AuthUser.Role.OWNER, null, merchantId), Instant.now());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLOSED", "MENU_SOLD_OUT", "OPTION_SOLD_OUT", "MENU_MISSING", "STORE_MISSING"})
    void verifiedPaymentThatCannotBePlacedIsRejectedAndRefunded(String unavailable) throws Exception {
        var order = createOrder();
        paid(order);
        makeUnavailable(unavailable);
        confirm(order, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PY013"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.refundStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.refundAmount").value(AMOUNT));
        assertRefundQueued(order);
        assertThat(jdbc.queryForObject("SELECT reject_reason_code FROM orders WHERE id=?", String.class, order.id()))
                .isEqualTo("CLOSED".equals(unavailable) ? "STORE_CLOSING" : "MENU_SOLD_OUT".equals(unavailable) ? "SOLD_OUT" : "OTHER");
        assertThat(paymentStatus(order)).isEqualTo("APPROVED");
    }

    @Test
    void refundWorkerCompletesSystemRejectionAndCustomerCanReadResult() throws Exception {
        var order = createOrder();
        paid(order);
        makeUnavailable("CLOSED");
        confirm(order, customer).andExpect(status().isConflict());
        when(client.cancelFullPayment(any(), anyString(), eq("ADMIN"), eq("주문 접수 불가: STORE_CLOSING")))
                .thenAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return true;
                });
        refunds.processBatch();
        confirm(order, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PY013"))
                .andExpect(jsonPath("$.data.refundStatus").value("SUCCEEDED"));
        mvc.perform(get("/api/v1/orders/{id}/payment", order.id()).header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.payment.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.refund.status").value("SUCCEEDED"));
        mvc.perform(get("/api/v1/orders/{id}", order.id()).header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rejectReason").value("STORE_CLOSING"))
                .andExpect(jsonPath("$.data.rejectReasonDetail").isNotEmpty())
                .andExpect(jsonPath("$.data.pickupNo").isEmpty()).andExpect(jsonPath("$.data.refundStatus").value("SUCCEEDED"));
        mvc.perform(get("/api/v1/orders").param("filter", "HISTORY").header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].orderId").value(order.id()));
        mvc.perform(get("/api/v1/staff/stores/{id}/orders/active", storeId).header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.orders").isEmpty());
        verify(client, times(1)).getPaidPayment(order.paymentId(), STORE);
        assertRefundQueued(order);
    }

    @Test
    void webhookReplayCannotResurrectRejectedOrderAfterStoreReopens() throws Exception {
        var order = createOrder();
        paid(order);
        makeUnavailable("CLOSED");
        webhook(order, STORE).andExpect(status().isNoContent());
        jdbc.update("UPDATE store SET status='OPEN' WHERE id=?", storeId);
        webhook(order, STORE).andExpect(status().isNoContent());
        confirm(order, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PY013"));
        assertRefundQueued(order);
        verify(client, times(1)).getPaidPayment(order.paymentId(), STORE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "WITHDRAWN"})
    void webhookRefundDoesNotRequireMemberToRemainActive(String status) throws Exception {
        var order = createOrder();
        paid(order);
        jdbc.update("UPDATE member SET status=? WHERE id=?", status, memberId);
        webhook(order, STORE).andExpect(status().isNoContent());
        assertRefundQueued(order);
    }

    @Test
    void memberDeactivationDuringProviderLookupQueuesRefundInsteadOfLosingPaidPayment() throws Exception {
        var order = createOrder();
        when(client.getPaidPayment(order.paymentId(), STORE)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            jdbc.update("UPDATE member SET status='SUSPENDED' WHERE id=?", memberId);
            return payment(order, AMOUNT, CHANNEL);
        });
        confirm(order, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PY013"));
        assertRefundQueued(order);
    }

    @Test
    void queueFailureRollsBackRejectionApprovalAndHistoryThenRetryCanRecover() throws Exception {
        var order = createOrder();
        paid(order);
        makeUnavailable("CLOSED");
        PaymentCancellationApi target = AopTestUtils.getUltimateTargetObject(cancellations);
        doThrow(new BusinessException(PaymentErrorCode.SETUP_NOT_READY)).when(target)
                .requestUnplaceablePayment(eq(order.id()), eq(AMOUNT), anyString());
        confirm(order, customer).andExpect(status().isServiceUnavailable());
        assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(paymentStatus(order)).isEqualTo("READY");
        assertThat(refundCount(order)).isZero();
        assertThat(rejectionCount(order)).isZero();
        assertThat(jdbc.queryForObject("SELECT rejected_at FROM orders WHERE id=?", Object.class, order.id())).isNull();
        doCallRealMethod().when(target).requestUnplaceablePayment(eq(order.id()), eq(AMOUNT), anyString());
        confirm(order, customer).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PY013"));
        assertRefundQueued(order);
    }

    @Test
    void concurrentAppAndWebhookCreateExactlyOneSystemRefund() throws Exception {
        var order = createOrder();
        makeUnavailable("MENU_SOLD_OUT");
        var both = new CountDownLatch(2);
        when(client.getPaidPayment(order.paymentId(), STORE)).thenAnswer(call -> {
            both.countDown();
            assertThat(both.await(5, TimeUnit.SECONDS)).isTrue();
            return payment(order, AMOUNT, CHANNEL);
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var app = pool.submit(() -> confirm(order, customer).andReturn().getResponse().getStatus());
            var hook = pool.submit(() -> webhook(order, STORE).andReturn().getResponse().getStatus());
            assertThat(app.get(10, TimeUnit.SECONDS)).isEqualTo(409);
            assertThat(hook.get(10, TimeUnit.SECONDS)).isEqualTo(204);
        }
        assertRefundQueued(order);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REQUESTED", "ACCEPTED", "MAKING", "READY", "COMPLETED"})
    void placedOrdersAreNotRetroactivelyRefundedWhenStoreCloses(String targetStatus) throws Exception {
        var order = createOrder();
        paid(order);
        confirm(order, customer).andExpect(status().isOk());
        List<String> actions = List.of("accept", "start", "ready", "complete");
        int count = List.of("REQUESTED", "ACCEPTED", "MAKING", "READY", "COMPLETED").indexOf(targetStatus);
        for (int i = 0; i < count; i++) {
            mvc.perform(post("/api/v1/staff/orders/{id}/" + actions.get(i), order.id()).header("Authorization", owner))
                    .andExpect(status().isOk());
        }
        makeUnavailable("CLOSED");
        confirm(order, customer).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(targetStatus));
        webhook(order, STORE).andExpect(status().isNoContent());
        assertThat(refundCount(order)).isZero();
        assertThat(orderStatus(order)).isEqualTo(targetStatus);
        verify(client, times(1)).getPaidPayment(order.paymentId(), STORE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "channel"})
    void mismatchedProviderPaymentCannotTriggerAutomaticRefund(String mismatch) throws Exception {
        var order = createOrder();
        makeUnavailable("CLOSED");
        when(client.getPaidPayment(order.paymentId(), STORE)).thenReturn(payment(order,
                "amount".equals(mismatch) ? AMOUNT + 1 : AMOUNT, "channel".equals(mismatch) ? "wrong" : CHANNEL));
        confirm(order, customer).andExpect(status().is("amount".equals(mismatch) ? 400 : 409))
                .andExpect(jsonPath("$.code").value("amount".equals(mismatch) ? "PY001" : "PY011"));
        assertThat(refundCount(order)).isZero();
        assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(paymentStatus(order)).isEqualTo("READY");
    }

    @Test
    void providerFailureIsRetriedWithoutPrematureRejection() throws Exception {
        var order = createOrder();
        makeUnavailable("CLOSED");
        when(client.getPaidPayment(order.paymentId(), STORE))
                .thenThrow(new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE))
                .thenReturn(payment(order, AMOUNT, CHANNEL));
        confirm(order, customer).andExpect(status().isServiceUnavailable());
        assertThat(refundCount(order)).isZero();
        assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
        confirm(order, customer).andExpect(status().isConflict());
        assertRefundQueued(order);
    }

    @Test
    void unrelatedCustomerAndWrongWebhookStoreCannotTriggerRefund() throws Exception {
        var order = createOrder();
        makeUnavailable("CLOSED");
        long other = fixtures.createMember(schoolId, UUID.randomUUID() + "@test.ac.kr", "ACTIVE");
        confirm(order, bearer(other)).andExpect(status().isNotFound());
        webhook(order, "wrong-store").andExpect(status().isConflict());
        verifyNoInteractions(client);
        assertThat(refundCount(order)).isZero();
    }

    @Test
    void schemaStillRequiresPickupFieldsForAcceptedOrdersAndValidRejectedMetadata() throws Exception {
        var order = createOrder();
        assertPlacementConstraintRejects(() -> jdbc.update("UPDATE orders SET status='ACCEPTED' WHERE id=?", order.id()));
        assertPlacementConstraintRejects(() -> jdbc.update("UPDATE orders SET status='REJECTED',reject_reason_code='OTHER' WHERE id=?", order.id()));
        assertPlacementConstraintRejects(() -> jdbc.update("UPDATE orders SET status='REJECTED',reject_reason_code='OTHER',rejected_at=UTC_TIMESTAMP(6),pickup_no=1 WHERE id=?", order.id()));
        assertThat(orderStatus(order)).isEqualTo("PENDING_PAYMENT");
    }

    private void assertPlacementConstraintRejects(Runnable update) {
        assertThatThrownBy(update::run).isInstanceOfSatisfying(DataAccessException.class, error -> {
            assertThat(error.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
                assertThat(cause.getErrorCode()).isEqualTo(3819);
                assertThat(cause.getMessage()).contains("ck_orders_placed");
            });
        });
    }

    private void makeUnavailable(String kind) {
        switch (kind) {
            case "CLOSED" -> jdbc.update("UPDATE store SET status='CLOSED' WHERE id=?", storeId);
            case "MENU_SOLD_OUT" -> jdbc.update("UPDATE menu_item SET is_sold_out=TRUE WHERE id=?", menuId);
            case "OPTION_SOLD_OUT" -> jdbc.update("UPDATE option_item SET is_sold_out=TRUE WHERE id=?", optionId);
            case "MENU_MISSING" -> {
                MenuQueryApi target = AopTestUtils.getUltimateTargetObject(menus);
                doReturn(Optional.empty()).when(target).findMenuItem(storeId, menuId);
            }
            case "STORE_MISSING" -> {
                StoreQueryApi target = AopTestUtils.getUltimateTargetObject(stores);
                doReturn(Optional.empty()).when(target).findById(storeId);
            }
            default -> throw new IllegalArgumentException("알 수 없는 테스트 상황");
        }
    }

    private Checkout createOrder() throws Exception {
        String body = mvc.perform(post("/api/v1/orders").header("Authorization", customer)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("storeId", storeId, "withdrawalLimitAgreed", true,
                                "items", List.of(Map.of("menuId", menuId, "quantity", 1, "optionItemIds", List.of(optionId)))))))
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
        String id = "compensation-webhook-id";
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(PortOneOrderPaymentIntegrationTest.TEST_SIGNING_KEY), "HmacSHA256"));
        String signature = "v1," + Base64.getEncoder().encodeToString(
                mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        return mvc.perform(post("/api/v1/payments/webhooks/portone").contentType(MediaType.APPLICATION_JSON)
                .header("webhook-id", id).header("webhook-timestamp", timestamp).header("webhook-signature", signature).content(body));
    }

    private void paid(Checkout order) {
        when(client.getPaidPayment(order.paymentId(), STORE)).thenReturn(payment(order, AMOUNT, CHANNEL));
    }

    private PortOnePaymentClient.PaidPayment payment(Checkout order, int amount, String channel) {
        return new PortOnePaymentClient.PaidPayment(order.paymentId(), "transaction-" + order.id(), STORE,
                channel, "INICIS_V2", "V2", "KRW", amount, "CARD", Instant.now());
    }

    private void assertRefundQueued(Checkout order) {
        assertThat(orderStatus(order)).isEqualTo("REJECTED");
        assertThat(refundCount(order)).isEqualTo(1);
        assertThat(rejectionCount(order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT requested_by_type FROM payment_cancel c JOIN payment p ON p.id=c.payment_id WHERE p.order_id=?", String.class, order.id())).isEqualTo("SYSTEM");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='REQUESTED'", Integer.class, order.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id=? AND (pickup_no IS NOT NULL OR placed_at IS NOT NULL OR business_date IS NOT NULL)", Integer.class, order.id())).isZero();
    }

    private String orderStatus(Checkout order) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order.id());
    }

    private String paymentStatus(Checkout order) {
        return jdbc.queryForObject("SELECT status FROM payment WHERE order_id=?", String.class, order.id());
    }

    private int refundCount(Checkout order) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payment_cancel c JOIN payment p ON p.id=c.payment_id WHERE p.order_id=?", Integer.class, order.id());
    }

    private int rejectionCount(Checkout order) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='REJECTED'", Integer.class, order.id());
    }

    private String bearer(long id) {
        return "Bearer " + tokens.encode(new AuthUser(id, AuthUser.SubjectType.MEMBER,
                AuthUser.Role.CUSTOMER, schoolId, null), Instant.now());
    }

    private record Checkout(long id, String paymentId) { }
}
