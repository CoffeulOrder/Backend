package com.coffeul.payment;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.common.error.BusinessException;
import com.coffeul.payment.api.PaymentCancellationApi;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.application.PaymentCancellationGateway;
import com.coffeul.payment.application.PaymentCancellationProcessor;
import com.coffeul.payment.infrastructure.PaymentCancellationRepository;
import com.coffeul.payment.infrastructure.PortOnePaymentClient;
import com.coffeul.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("dev")
@TestPropertySource(properties = {"coffeul.payment.gateway=fake", "coffeul.dev.payment-flow-enabled=true",
        "coffeul.payment.cancel.enabled=false"})
class PaymentCancellationIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentCancellationApi api;
    @Autowired PaymentCancellationRepository repository;
    @Autowired PaymentCancellationProcessor processor;
    @Autowired PlatformTransactionManager transactionManager;
    private TransactionTemplate transaction;
    private TestFixtures fixtures;
    private long school;
    private long member;
    private long merchant;
    private long store;
    private final List<Long> orders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        fixtures = new TestFixtures(jdbc);
        school = fixtures.createSchool("cancel-" + UUID.randomUUID());
        member = fixtures.createMember(school, UUID.randomUUID() + "@test.invalid", "ACTIVE");
        merchant = fixtures.createMerchant(UUID.randomUUID().toString().substring(0, 10), new BigDecimal("0.0300"));
        store = fixtures.createStore(merchant, school, "refund-test", "OPEN");
    }

    @AfterEach
    void removeQueueRows() {
        // Scope cleanup to this test's fixtures, including interrupted-lease cases.
        for (long order : orders) {
            jdbc.update("DELETE c FROM payment_cancel c JOIN payment p ON p.id = c.payment_id WHERE p.order_id = ?", order);
        }
    }

    @Test
    void queueRequiresTransactionAndReusesOneUuidAndRecordOnDuplicate() {
        long order = approved("FAKE");
        assertThatThrownBy(() -> api.requestCustomerCancellation(order, 4000))
                .isInstanceOf(IllegalTransactionStateException.class);
        var first = queue(order);
        var second = queue(order);
        assertThat(second).isEqualTo(first);
        assertThat(first.status()).isEqualTo("REQUESTED");
        var row = jdbc.queryForMap("SELECT * FROM payment_cancel WHERE id = ?", first.cancellationId());
        assertThat(row.get("requested_by_type")).isEqualTo("CUSTOMER");
        assertThat(row.get("cancel_reason")).isEqualTo("고객 요청");
        assertThat(UUID.fromString((String) row.get("idempotency_key"))).isNotNull();
        assertThat(count(order)).isEqualTo(1);
        assertThat(status(order)).isEqualTo("APPROVED");
    }

    @Test
    void staffRejectionQueuesPersistedActorAndReasonAndReusesSameRefund() {
        long order = approved("FAKE");
        String reason = "원두 품절로 제공 불가";
        assertThatThrownBy(() -> api.requestStaffRejection(order, 4000, reason))
                .isInstanceOf(IllegalTransactionStateException.class);
        var first = transaction.execute(tx -> api.requestStaffRejection(order, 4000, reason));
        var repeated = transaction.execute(tx -> api.requestStaffRejection(order, 4000, reason));
        assertThat(repeated).isEqualTo(first);
        assertThat(first.status()).isEqualTo("REQUESTED");
        var saved = repository.findRequestByOrderId(order).orElseThrow();
        assertThat(saved.requestedByType()).isEqualTo("STAFF");
        assertThat(saved.reason()).isEqualTo(reason);
        assertThat(count(order)).isEqualTo(1);
        assertThat(status(order)).isEqualTo("APPROVED");
        assertThat(processor.processBatch()).isZero();
        jdbc.update("UPDATE orders SET status = 'REJECTED', reject_reason_code = 'SOLD_OUT', rejected_at = UTC_TIMESTAMP(6) WHERE id = ?", order);
        assertThat(processor.processBatch()).isEqualTo(1);
        assertThat(refundStatus(order)).isEqualTo("SUCCEEDED");
        assertThat(status(order)).isEqualTo("CANCELED");
        var completed = transaction.execute(tx -> api.requestStaffRejection(order, 4000, reason));
        assertThat(completed).isEqualTo(api.findByOrderId(order).orElseThrow());
        assertThat(processor.processBatch()).isZero();
        assertThat(count(order)).isEqualTo(1);
        assertThat(attempts(order)).isEqualTo(1);
    }

    @Test
    void existingRefundCannotBeReusedWithDifferentActorReasonOrAmount() {
        long customerOrder = approved("FAKE");
        queue(customerOrder);
        assertThatThrownBy(() -> transaction.execute(tx -> api.requestStaffRejection(customerOrder, 4000, "고객 요청")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH));

        long staffOrder = approved("FAKE");
        transaction.execute(tx -> api.requestStaffRejection(staffOrder, 4000, "고객 요청"));
        assertThatThrownBy(() -> queue(staffOrder))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH));
        assertThatThrownBy(() -> transaction.execute(tx -> api.requestStaffRejection(staffOrder, 4000, "다른 사유")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH));
        assertThatThrownBy(() -> transaction.execute(tx -> api.requestStaffRejection(staffOrder, 1000, "고객 요청")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PaymentErrorCode.AMOUNT_MISMATCH));
        assertThat(count(customerOrder)).isEqualTo(1);
        assertThat(count(staffOrder)).isEqualTo(1);
        assertThat(repository.findRequestByOrderId(staffOrder).orElseThrow().reason()).isEqualTo("고객 요청");
    }

    @Test
    void existingCustomerRefundCannotBeReusedWithChangedSavedReason() {
        long order = approved("FAKE");
        var cancellation = queue(order);
        jdbc.update("UPDATE payment_cancel SET cancel_reason = '변경된 사유' WHERE id = ?", cancellation.cancellationId());
        assertThatThrownBy(() -> queue(order))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH));
        assertThat(count(order)).isEqualTo(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void staffRejectionRejectsMissingReason(String reason) {
        long order = approved("FAKE");
        assertThatThrownBy(() -> transaction.execute(tx -> api.requestStaffRejection(order, 4000, reason)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH));
        assertThat(count(order)).isZero();
    }

    @Test
    void staffRejectionEnforcesSavedReasonLengthBeforeQueueing() {
        long order = approved("FAKE");
        assertThatThrownBy(() -> transaction.execute(tx -> api.requestStaffRejection(order, 4000, "가".repeat(201))))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH));
        assertThat(count(order)).isZero();
        transaction.execute(tx -> api.requestStaffRejection(order, 4000, "가".repeat(200)));
        assertThat(repository.findRequestByOrderId(order).orElseThrow().reason()).isEqualTo("가".repeat(200));
    }

    @ParameterizedTest
    @CsvSource({"CUSTOMER,REJECTED", "STAFF,CANCELED", "CUSTOMER,REQUESTED", "STAFF,REQUESTED",
            "SYSTEM,CANCELED", "SYSTEM,REJECTED"})
    void invalidActorStatePairIsSkippedByBothDueQueryAndClaim(String actor, String orderStatus) {
        long order = approved("FAKE");
        var cancellation = queue(order);
        jdbc.update("UPDATE orders SET status = ?, reject_reason_code = 'SOLD_OUT' WHERE id = ?", orderStatus, order);
        jdbc.update("UPDATE payment_cancel SET requested_by_type = ? WHERE id = ?", actor, cancellation.cancellationId());
        Instant now = Instant.now();
        assertThat(repository.dueIds("FAKE", now, 100)).doesNotContain(cancellation.cancellationId());
        var claim = transaction.execute(tx -> repository.claim(cancellation.cancellationId(), "FAKE", now, now.plusSeconds(60)));
        assertThat(claim).isEmpty();
        assertThat(processor.processBatch()).isZero();
        assertThat(attempts(order)).isZero();
        assertThat(refundStatus(order)).isEqualTo("REQUESTED");
        assertThat(status(order)).isEqualTo("APPROVED");
    }

    @Test
    void rejectedPortOneOrderPassesPersistedReasonAndAdminRequesterOnEveryAttempt() {
        long order = approved("INICIS_V2");
        var client = mock(PortOnePaymentClient.class);
        var gateway = gateway(new MockEnvironment().withProperty("coffeul.payment.gateway", "portone"), client);
        var service = new com.coffeul.payment.application.PaymentCancellationService(repository, gateway);
        String reason = "우유 품절로 주문 제공 불가";
        var cancellation = transaction.execute(tx -> service.requestStaffRejection(order, 4000, reason));
        jdbc.update("UPDATE orders SET status = 'REJECTED', reject_reason_code = 'SOLD_OUT', rejected_at = UTC_TIMESTAMP(6) WHERE id = ?", order);
        String key = jdbc.queryForObject("SELECT idempotency_key FROM payment_cancel WHERE id = ?",
                String.class, cancellation.cancellationId());
        var worker = portOneWorker(client);
        when(client.cancelFullPayment(any(), eq(key), eq("ADMIN"), eq(reason)))
                .thenThrow(new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE)).thenReturn(true);
        worker.processBatch();
        assertThat(refundStatus(order)).isEqualTo("REQUESTED");
        expireLease(order);
        worker.processBatch();
        assertThat(refundStatus(order)).isEqualTo("SUCCEEDED");
        assertThat(status(order)).isEqualTo("CANCELED");
        var completed = transaction.execute(tx -> service.requestStaffRejection(order, 4000, reason));
        assertThat(completed).isEqualTo(api.findByOrderId(order).orElseThrow());
        assertThat(count(order)).isEqualTo(1);
        verify(client, times(2)).cancelFullPayment(any(), eq(key), eq("ADMIN"), eq(reason));
        verifyNoMoreInteractions(client);
    }

    @Test
    void rejectedOrderWithInactiveProviderIsNotClaimed() {
        long order = approved("INICIS_V2");
        var client = mock(PortOnePaymentClient.class);
        var service = new com.coffeul.payment.application.PaymentCancellationService(repository,
                gateway(new MockEnvironment().withProperty("coffeul.payment.gateway", "portone"), client));
        var cancellation = transaction.execute(tx -> service.requestStaffRejection(order, 4000, "품절"));
        jdbc.update("UPDATE orders SET status = 'REJECTED', reject_reason_code = 'SOLD_OUT', rejected_at = UTC_TIMESTAMP(6) WHERE id = ?", order);
        Instant now = Instant.now();
        assertThat(repository.dueIds("FAKE", now, 100)).doesNotContain(cancellation.cancellationId());
        var claim = transaction.execute(tx -> repository.claim(cancellation.cancellationId(), "FAKE", now, now.plusSeconds(60)));
        assertThat(claim).isEmpty();
        assertThat(processor.processBatch()).isZero();
        assertThat(attempts(order)).isZero();
        assertThat(refundStatus(order)).isEqualTo("REQUESTED");
        verifyNoInteractions(client);
    }

    @Test
    void queueRollsBackWithOrderTransaction() {
        long order = approved("FAKE");
        assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> {
            api.requestCustomerCancellation(order, 4000);
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count(order)).isZero();
        assertThat(api.findByOrderId(order)).isEmpty();
    }

    @Test
    void partialAmountAndInactiveProviderCannotQueue() {
        long fake = approved("FAKE");
        assertThatThrownBy(() -> transaction.execute(tx -> api.requestCustomerCancellation(fake, 1000)))
                .isInstanceOf(BusinessException.class);
        long other = approved("INICIS_V2");
        assertThatThrownBy(() -> queue(other)).isInstanceOf(BusinessException.class);
        assertThat(count(fake)).isZero();
        assertThat(count(other)).isZero();
    }

    @Test
    void schedulerDisabledButManualBatchCompletesPaymentAndCancellationAtomically() {
        long order = approved("FAKE");
        queue(order);
        processor.scheduledProcess();
        assertThat(refundStatus(order)).isEqualTo("REQUESTED");
        assertThat(status(order)).isEqualTo("APPROVED");
        processor.processBatch();
        assertThat(refundStatus(order)).isEqualTo("SUCCEEDED");
        assertThat(status(order)).isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject("SELECT canceled_at IS NOT NULL FROM payment WHERE order_id = ?", Boolean.class, order)).isTrue();
        assertThat(queue(order).status()).isEqualTo("SUCCEEDED");
        assertThat(count(order)).isEqualTo(1);
    }

    @Test
    void workerWaitsForCommittedCanceledOrderAndSkipsInactiveProvider() {
        long order = approved("FAKE");
        jdbc.update("UPDATE orders SET status = 'REQUESTED' WHERE id = ?", order);
        queue(order);
        processor.processBatch();
        assertThat(refundStatus(order)).isEqualTo("REQUESTED");
        jdbc.update("UPDATE orders SET status = 'CANCELED' WHERE id = ?", order);
        var inactive = portOneWorker(mock(PortOnePaymentClient.class));
        inactive.processBatch();
        assertThat(attempts(order)).isZero();
        processor.processBatch();
        assertThat(refundStatus(order)).isEqualTo("SUCCEEDED");
    }

    @Test
    void retryAfterUnknownOutcomeKeepsSameIdempotencyAndOnlyThenMarksSuccess() {
        long order = approved("INICIS_V2");
        var client = mock(PortOnePaymentClient.class);
        queuePortOne(order, client);
        var worker = portOneWorker(client);
        when(client.cancelFullPayment(any(), anyString(), eq("CUSTOMER"), eq("고객 요청"))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
        }).thenReturn(true);
        worker.processBatch();
        assertThat(refundStatus(order)).isEqualTo("REQUESTED");
        assertThat(status(order)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT last_error_message FROM payment_cancel c JOIN payment p ON p.id=c.payment_id WHERE p.order_id=?", String.class, order)).isNull();
        expireLease(order);
        worker.processBatch();
        assertThat(refundStatus(order)).isEqualTo("SUCCEEDED");
        assertThat(status(order)).isEqualTo("CANCELED");
        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client, times(2)).cancelFullPayment(any(), keys.capture(), eq("CUSTOMER"), eq("고객 요청"));
        assertThat(keys.getAllValues()).allMatch(key -> key.equals(keys.getAllValues().getFirst()));
    }

    @Test
    void pendingRefundStaysRetryableAndMismatchFailsSafely() {
        long order = approved("INICIS_V2");
        var client = mock(PortOnePaymentClient.class);
        queuePortOne(order, client);
        var worker = portOneWorker(client);
        when(client.cancelFullPayment(any(), anyString(), eq("CUSTOMER"), eq("고객 요청"))).thenReturn(false);
        worker.processBatch();
        assertThat(refundStatus(order)).isEqualTo("REQUESTED");
        assertThat(status(order)).isEqualTo("APPROVED");
        expireLease(order);
        when(client.cancelFullPayment(any(), anyString(), eq("CUSTOMER"), eq("고객 요청")))
                .thenThrow(new BusinessException(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH));
        worker.processBatch();
        assertThat(refundStatus(order)).isEqualTo("FAILED");
        assertThat(status(order)).isEqualTo("APPROVED");
    }

    @Test
    void leasePreventsConcurrentCallAndStaleClaimCannotRegressSuccess() throws Exception {
        long order = approved("INICIS_V2");
        var client = mock(PortOnePaymentClient.class);
        queuePortOne(order, client);
        var worker = portOneWorker(client);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(client.cancelFullPayment(any(), anyString(), eq("CUSTOMER"), eq("고객 요청"))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return true;
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(worker::processBatch);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(executor.submit(worker::processBatch).get(10, TimeUnit.SECONDS)).isZero();
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(refundStatus(order)).isEqualTo("SUCCEEDED");
        assertThat(attempts(order)).isEqualTo(1);
        verify(client).cancelFullPayment(any(), anyString(), eq("CUSTOMER"), eq("고객 요청"));
    }

    @Test
    void expiredLeaseIsReclaimedAndStaleUpdatesCannotOverwriteNewClaim() {
        long order = approved("FAKE");
        var cancellation = queue(order);
        Instant now = Instant.now();
        var stale = transaction.execute(tx -> repository.claim(cancellation.cancellationId(), "FAKE", now, now.plusSeconds(60)).orElseThrow());
        var leased = transaction.execute(tx -> repository.claim(cancellation.cancellationId(), "FAKE", now, now.plusSeconds(60)));
        assertThat(leased).isEmpty();
        expireLease(order);
        var current = transaction.execute(tx -> repository.claim(cancellation.cancellationId(), "FAKE", now, now.plusSeconds(60)).orElseThrow());
        transaction.executeWithoutResult(tx -> {
            repository.fail(stale, "STALE");
            repository.retry(stale, now, "STALE");
            repository.succeed(stale, now);
        });
        assertThat(refundStatus(order)).isEqualTo("REQUESTED");
        assertThat(status(order)).isEqualTo("APPROVED");
        transaction.executeWithoutResult(tx -> repository.succeed(current, now));
        transaction.executeWithoutResult(tx -> {
            repository.fail(stale, "STALE");
            repository.retry(stale, now, "STALE");
        });
        assertThat(refundStatus(order)).isEqualTo("SUCCEEDED");
        assertThat(status(order)).isEqualTo("CANCELED");
    }

    @Test
    void finalTransactionRollbackLeavesBothRecordsPendingForRecovery() {
        long order = approved("FAKE");
        var cancellation = queue(order);
        Instant now = Instant.now();
        var claim = transaction.execute(tx -> repository.claim(cancellation.cancellationId(), "FAKE", now, now.plusSeconds(60)).orElseThrow());
        assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> {
            repository.succeed(claim, now);
            throw new IllegalStateException("commit failure simulation");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(refundStatus(order)).isEqualTo("REQUESTED");
        assertThat(status(order)).isEqualTo("APPROVED");
        expireLease(order);
        processor.processBatch();
        assertThat(refundStatus(order)).isEqualTo("SUCCEEDED");
        assertThat(status(order)).isEqualTo("CANCELED");
    }

    @Test
    void boundedBatchDoesNotTakeAllQueuedWork() {
        long first = approved("FAKE");
        long second = approved("FAKE");
        queue(first);
        queue(second);
        var env = new MockEnvironment().withProperty("coffeul.dev.payment-flow-enabled", "true");
        env.setActiveProfiles("dev");
        var worker = new PaymentCancellationProcessor(repository, gateway(env, null), transactionManager, false, 1, 60, 1);
        assertThat(worker.processBatch()).isEqualTo(1);
        assertThat(List.of(refundStatus(first), refundStatus(second)))
                .containsExactlyInAnyOrder("SUCCEEDED", "REQUESTED");
    }

    @Test
    void queuedAmountTamperingNeverRefunds() {
        long order = approved("INICIS_V2");
        var client = mock(PortOnePaymentClient.class);
        var cancellation = queuePortOne(order, client);
        jdbc.update("UPDATE payment_cancel SET cancel_amount=1000 WHERE id=?", cancellation.cancellationId());
        portOneWorker(client).processBatch();
        assertThat(refundStatus(order)).isEqualTo("FAILED");
        assertThat(status(order)).isEqualTo("APPROVED");
        verifyNoInteractions(client);
    }

    @Test
    void manualWorkerRejectsAnOpenOrderTransaction() {
        assertThatThrownBy(() -> transaction.execute(tx -> processor.processBatch()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CANCEL_WORKER_REQUIRES_NO_TRANSACTION");
    }

    @Test
    void unsupportedActorIsNotClaimedByCustomerWorker() {
        long order = approved("FAKE");
        var cancellation = queue(order);
        jdbc.update("UPDATE payment_cancel SET requested_by_type='STAFF' WHERE id=?", cancellation.cancellationId());
        processor.processBatch();
        assertThat(attempts(order)).isZero();
        assertThat(refundStatus(order)).isEqualTo("REQUESTED");
        assertThat(status(order)).isEqualTo("APPROVED");
    }

    private PaymentCancellationApi.Cancellation queue(long order) {
        return transaction.execute(tx -> api.requestCustomerCancellation(order, 4000));
    }

    private PaymentCancellationApi.Cancellation queuePortOne(long order, PortOnePaymentClient client) {
        var service = new com.coffeul.payment.application.PaymentCancellationService(repository,
                gateway(new MockEnvironment().withProperty("coffeul.payment.gateway", "portone"), client));
        return transaction.execute(tx -> service.requestCustomerCancellation(order, 4000));
    }

    private PaymentCancellationProcessor portOneWorker(PortOnePaymentClient client) {
        return new PaymentCancellationProcessor(repository,
                gateway(new MockEnvironment().withProperty("coffeul.payment.gateway", "portone"), client),
                transactionManager, false, 20, 60, 1);
    }

    @SuppressWarnings("unchecked")
    private PaymentCancellationGateway gateway(MockEnvironment env, PortOnePaymentClient client) {
        ObjectProvider<PortOnePaymentClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        when(provider.getObject()).thenReturn(client);
        return new PaymentCancellationGateway(env, provider);
    }

    private long approved(String provider) {
        long order = fixtures.createOrder(member, store, school, "CANCELED");
        orders.add(order);
        String pgId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT IGNORE INTO merchant_pg (merchant_id, pg_provider, mid, client_key, secret_ref)
                VALUES (?, ?, ?, 'dummy', 'dummy-ref')
                """, merchant, provider, "dummy-" + pgId);
        long config = jdbc.queryForObject("SELECT id FROM merchant_pg WHERE merchant_id=? AND pg_provider=?", Long.class, merchant, provider);
        jdbc.update("""
                INSERT INTO payment (order_id, store_id, merchant_pg_id, pg_provider, attempt_no, pg_order_id,
                    payment_key, amount, status, approved_at, confirm_idempotency_key, portone_store_id, portone_channel_key)
                VALUES (?, ?, ?, ?, 1, ?, ?, 4000, 'APPROVED', UTC_TIMESTAMP(6), ?, 'store-test', 'channel-test')
                """, order, store, config, provider, pgId, "tx-" + pgId, UUID.randomUUID().toString());
        return order;
    }

    private void expireLease(long order) {
        jdbc.update("""
                UPDATE payment_cancel c JOIN payment p ON p.id=c.payment_id
                SET c.next_retry_at=UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE p.order_id=?
                """, order);
    }

    private int count(long order) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payment_cancel c JOIN payment p ON p.id=c.payment_id WHERE p.order_id=?", Integer.class, order);
    }

    private int attempts(long order) {
        return jdbc.queryForObject("SELECT attempt_count FROM payment_cancel c JOIN payment p ON p.id=c.payment_id WHERE p.order_id=?", Integer.class, order);
    }

    private String status(long order) {
        return jdbc.queryForObject("SELECT status FROM payment WHERE order_id=?", String.class, order);
    }

    private String refundStatus(long order) {
        return api.findByOrderId(order).orElseThrow().status();
    }
}
