package com.coffeul.payment.api;

import java.time.Instant;

/** 실제 PG 호출을 하지 않는 개발용 포트. dev 프로필과 별도 활성화가 모두 필요하다. */
public interface FakePaymentApi {
    State findAttempt(Long orderId, String pgOrderId);
    State approve(Long orderId, String pgOrderId, int expectedAmount, Instant now);
    record State(String status, int amount, Instant approvedAt) { }
}
