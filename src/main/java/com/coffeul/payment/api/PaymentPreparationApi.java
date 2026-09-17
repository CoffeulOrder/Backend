package com.coffeul.payment.api;

/**
 * 주문 생성 시 결제창에 필요한 정보를 준비하는 포트 (SYNC_CALLS 취지와 동일한 방향 — order가 payment.api만 호출).
 * <p><b>주의</b>: 이 인터페이스는 결제 시도 1건(READY)을 실제 {@code payment} 테이블에 저장하지 않는다.
 * {@code payment.merchant_pg_id}가 가리킬 {@code merchant_pg} row가 아직 없기 때문(사업자 정보 미확정).
 * merchant · store 실데이터가 생기면 이 구현을 영속화하도록 이어서 배선한다.
 */
public interface PaymentPreparationApi {

    PaymentAttemptView prepareFirstAttempt(String orderCode, int amount);
}
