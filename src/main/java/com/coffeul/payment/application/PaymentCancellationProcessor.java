package com.coffeul.payment.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.infrastructure.PaymentCancellationRepository;
import com.coffeul.payment.infrastructure.PaymentCancellationRepository.Claim;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Component
public class PaymentCancellationProcessor {
    private static final Logger log = LoggerFactory.getLogger(PaymentCancellationProcessor.class);
    private final PaymentCancellationRepository repository;
    private final PaymentCancellationGateway gateway;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final int batchSize;
    private final long leaseSeconds;
    private final long retrySeconds;

    public PaymentCancellationProcessor(PaymentCancellationRepository repository, PaymentCancellationGateway gateway,
            PlatformTransactionManager transactionManager,
            @Value("${coffeul.payment.cancel.enabled:true}") boolean enabled,
            @Value("${coffeul.payment.cancel.batch-size:20}") int batchSize,
            @Value("${coffeul.payment.cancel.lease-seconds:60}") long leaseSeconds,
            @Value("${coffeul.payment.cancel.retry-seconds:30}") long retrySeconds) {
        this.repository = repository;
        this.gateway = gateway;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.enabled = enabled;
        this.batchSize = Math.clamp(batchSize, 1, 100);
        this.leaseSeconds = Math.clamp(leaseSeconds, 30, 3600);
        this.retrySeconds = Math.clamp(retrySeconds, 1, 3600);
    }

    @Scheduled(fixedDelayString = "${coffeul.payment.cancel.interval-ms:10000}",
            initialDelayString = "${coffeul.payment.cancel.initial-delay-ms:10000}")
    public void scheduledProcess() {
        if (enabled) {
            processBatch();
        }
    }

    /** Same durable claim path as the scheduler. Never call from an order transaction. */
    public int processBatch() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("CANCEL_WORKER_REQUIRES_NO_TRANSACTION");
        }
        String provider = gateway.activeProvider();
        if (provider == null) {
            return 0;
        }
        int claimed = 0;
        for (long id : repository.dueIds(provider, Instant.now(), batchSize)) {
            try {
                Instant now = Instant.now();
                var claim = transaction.execute(status -> repository.claim(id, provider, now, now.plusSeconds(leaseSeconds)));
                if (claim != null && claim.isPresent()) {
                    process(claim.get());
                    claimed++;
                }
            } catch (RuntimeException error) {
                // A database outage must leave the durable lease available for later recovery.
                log.warn("CANCEL_BATCH_ITEM_DEFERRED");
            }
        }
        return claimed;
    }

    private void process(Claim claim) {
        try {
            boolean succeeded = gateway.cancel(claim);
            transaction.executeWithoutResult(status -> {
                if (succeeded) {
                    repository.succeed(claim, Instant.now());
                } else {
                    repository.retry(claim, nextRetry(claim), "PG_CANCEL_PENDING");
                }
            });
        } catch (BusinessException error) {
            var code = error.errorCode();
            transaction.executeWithoutResult(status -> {
                if (code == PaymentErrorCode.AMOUNT_MISMATCH || code == PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH) {
                    repository.fail(claim, code.code());
                } else {
                    repository.retry(claim, nextRetry(claim), code.code());
                }
            });
        } catch (RuntimeException error) {
            // Unknown outcomes, including a failed DB commit after a PG refund, must be reconciled again.
            // Persist only a fixed code. Exception messages may contain PG bodies or credentials.
            transaction.executeWithoutResult(status -> repository.retry(claim, nextRetry(claim), "CANCEL_UNKNOWN"));
        }
    }

    private Instant nextRetry(Claim claim) {
        return Instant.now().plusSeconds(Math.min(3600, retrySeconds * Math.min(claim.attempt(), 10)));
    }
}
