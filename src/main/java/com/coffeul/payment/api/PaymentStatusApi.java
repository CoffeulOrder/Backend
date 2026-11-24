package com.coffeul.payment.api;

import java.time.Instant;
import java.util.Optional;

/** PG 자격증명·거래 키·오류 원문을 포함하지 않는 결제 상태 조회. */
public interface PaymentStatusApi {
    Optional<Snapshot> findLatest(long orderId);

    record Snapshot(int attemptNo, String pgProvider, String pgOrderId, String status, int amount,
                    String method, Instant approvedAt, Instant canceledAt, Refund refund) { }
    record Refund(String status, int amount, Instant succeededAt) { }
}
