package com.coffeul.order.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.api.PaymentRecoveryApi;
import com.coffeul.payment.api.PaymentRecoveryApi.Claim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/** 웹훅·앱 복귀가 누락된 결제를 기한 전부터 재조회하고, 만료 뒤에는 환불로 복구한다. */
@Component
@ConditionalOnProperty(name = "coffeul.payment.gateway", havingValue = "portone")
public class PaymentReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);
    private final PaymentRecoveryApi recovery;
    private final PortOneOrderPaymentService payments;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final int batchSize;
    private final long leaseSeconds;
    private final long retrySeconds;
    private final long pendingGraceSeconds;

    public PaymentReconciliationJob(PaymentRecoveryApi recovery, PortOneOrderPaymentService payments,
            PlatformTransactionManager manager,
            @Value("${coffeul.payment.recovery.enabled:true}") boolean enabled,
            @Value("${coffeul.payment.recovery.batch-size:20}") int batchSize,
            @Value("${coffeul.payment.recovery.lease-seconds:60}") long leaseSeconds,
            @Value("${coffeul.payment.recovery.retry-seconds:60}") long retrySeconds,
            @Value("${coffeul.payment.recovery.pending-grace-seconds:60}") long pendingGraceSeconds) {
        this.recovery = recovery;
        this.payments = payments;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.enabled = enabled;
        this.batchSize = Math.clamp(batchSize, 1, 100);
        this.leaseSeconds = Math.clamp(leaseSeconds, 30, 3600);
        this.retrySeconds = Math.clamp(retrySeconds, 1, 3600);
        this.pendingGraceSeconds = Math.clamp(pendingGraceSeconds, 10, 3600);
    }

    @Scheduled(fixedDelayString = "${coffeul.payment.recovery.interval-ms:60000}",
            initialDelayString = "${coffeul.payment.recovery.initial-delay-ms:60000}")
    public void scheduledProcess() {
        if (enabled) processBatch();
    }

    public int processBatch() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("RECOVERY_IO_REQUIRES_NO_TRANSACTION");
        }
        Instant discoveryTime = Instant.now();
        transaction.executeWithoutResult(status -> recovery.discover(discoveryTime,
                discoveryTime.minusSeconds(pendingGraceSeconds), batchSize));
        int claimed = 0;
        for (long id : recovery.dueIds(Instant.now(), batchSize)) {
            try {
                Instant now = Instant.now();
                var claim = transaction.execute(status -> recovery.claim(id, now, now.plusSeconds(leaseSeconds)));
                if (claim != null && claim.isPresent()) {
                    process(claim.get());
                    claimed++;
                }
            } catch (RuntimeException error) {
                // PG 원문·자격증명은 로그에 남기지 않는다. 리스가 만료되면 다시 처리한다.
                log.warn("PAYMENT_RECOVERY_DEFERRED");
            }
        }
        return claimed;
    }

    private void process(Claim claim) {
        try {
            payments.recoverUnconfirmedPayment(claim.orderId(), claim.pgPaymentId());
            transaction.executeWithoutResult(status -> recovery.complete(claim));
        } catch (BusinessException error) {
            var code = error.errorCode();
            transaction.executeWithoutResult(status -> {
                if (code == PaymentErrorCode.AMOUNT_MISMATCH || code == PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH
                        || code == PaymentErrorCode.INVALID_STATE || code == PaymentErrorCode.ATTEMPT_NOT_FOUND) {
                    recovery.fail(claim, code.code());
                } else {
                    recovery.retry(claim, nextRetry(claim), code.code());
                }
            });
        } catch (RuntimeException error) {
            transaction.executeWithoutResult(status -> recovery.retry(claim, nextRetry(claim), "RECOVERY_UNKNOWN"));
        }
    }

    private Instant nextRetry(Claim claim) {
        Instant now = Instant.now();
        long delay = Math.min(3600, retrySeconds * Math.min(claim.attempt(), 60));
        if ("PENDING_PAYMENT".equals(claim.orderStatus()) && claim.expiresAt().isAfter(now)) {
            // 주문이 유효한 동안에는 재조회 대기를 늘리지 않는다.
            delay = Math.min(60, retrySeconds);
        }
        return now.plusSeconds(delay);
    }
}
