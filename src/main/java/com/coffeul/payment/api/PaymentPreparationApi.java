package com.coffeul.payment.api;

/**
 * 주문 생성 시 결제창에 필요한 정보를 준비하는 포트 (SYNC_CALLS 취지와 동일한 방향 — order가 payment.api만 호출).
 * 개발용 영속화 구현은 명시적으로 활성화할 때만 사용한다. 실제 PG 연결은 별도 구현 대상이다.
 */
public interface PaymentPreparationApi {

    PaymentAttemptView prepareFirstAttempt(Long orderId, Long storeId, String orderCode, int amount);

    /** 기본 가짜 어댑터는 저장 상태가 없다. 영속화 구현은 만료된 주문의 READY 시도를 닫는다. */
    default void expireReadyAttempts(Long orderId, java.time.Instant now) {
    }
}
