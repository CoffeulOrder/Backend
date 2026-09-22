package com.coffeul.order.api;

/**
 * MS-25(픽업 대기) 커밋 후 발행 (rules.py ORDER_RULES: "다른 모듈에 알릴 일은 커밋 후 이벤트로만").
 * notification 모듈이 이 이벤트로 고객에게 픽업 번호를 푸시한다 (아직 notification 구현 전 — 태완 형 담당).
 */
public record OrderReadyEvent(Long orderId, Long storeId, Long memberId, Short pickupNo) {
}
