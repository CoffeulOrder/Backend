package com.coffeul.payment.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 결제 알림 누락·만료 주문의 대사를 위한 영속 작업. 외부 PG 조회는 트랜잭션 밖에서 한다. */
public interface PaymentRecoveryApi {
    /** 기존 만료 건만 탐색하는 호출의 의미를 유지한다. */
    default void discover(Instant now, int limit) {
        discover(now, Instant.EPOCH, limit);
    }
    void discover(Instant now, Instant pendingCreatedBefore, int limit);
    List<Long> dueIds(Instant now, int limit);
    Optional<Claim> claim(long paymentId, Instant now, Instant leaseUntil);
    void complete(Claim claim);
    void retry(Claim claim, Instant retryAt, String safeCode);
    void fail(Claim claim, String safeCode);

    record Claim(long paymentId, long orderId, String pgPaymentId, int attempt,
                 String orderStatus, Instant expiresAt) { }
}
