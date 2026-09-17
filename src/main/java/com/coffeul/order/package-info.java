/**
 * 주문 생성 · 상태 전이(PENDING_PAYMENT → REQUESTED → ACCEPTED → MAKING → READY → COMPLETED,
 * 분기 CANCELED/REJECTED/EXPIRED), 상태 이력.
 * 담당: 민섭. 규칙: 금액 = 합계 − 할인, 수락 전만 고객 취소, 모든 전이는 조건부 UPDATE.
 * payment → order는 직접 호출(같은 트랜잭션), order → payment/notification은 이벤트로만.
 */
package com.coffeul.order;
