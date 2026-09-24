package com.coffeul.order.api;

/**
 * 결제 대기 만료 작업 커밋 후 발행 (rules.py JOBS: "결제 대기 만료" → EXPIRED + 이력 → OrderExpired).
 * payment 모듈이 이 이벤트로 진행 중이던 결제 시도를 FAILED(ORDER_EXPIRED)로 정리한다
 * (아직 payment가 결제 시도를 영속화하지 않아 — PaymentPreparationApi 참고 — 지금은 리스너가 없다).
 */
public record OrderExpiredEvent(Long orderId, Long memberId) {
}
