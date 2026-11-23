package com.coffeul.payment.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 만료 주문의 결제 대사를 위한 영속 작업. 외부 PG 조회는 이 포트의 트랜잭션 밖에서 한다. */
public interface PaymentRecoveryApi {
    void discover(Instant now, int limit);
    List<Long> dueIds(Instant now, int limit);
    Optional<Claim> claim(long paymentId, Instant now, Instant leaseUntil);
    void complete(Claim claim);
    void retry(Claim claim, Instant retryAt, String safeCode);
    void fail(Claim claim, String safeCode);

    record Claim(long paymentId, long orderId, String pgPaymentId, int attempt) { }
}
