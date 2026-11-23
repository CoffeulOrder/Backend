package com.coffeul.payment.api;

import java.util.Optional;

/** 주문 취소 트랜잭션에서 전액 환불 요청을 저장한다. */
public interface PaymentCancellationApi {

    Cancellation requestCustomerCancellation(Long orderId, int amount);

    Optional<Cancellation> findByOrderId(Long orderId);

    record Cancellation(long cancellationId, String status, int amount) {
    }
}
