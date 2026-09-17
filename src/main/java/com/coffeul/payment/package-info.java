/**
 * PG 결제 승인/재시도/취소(환불), 멱등 키로 중복 결제 방지, 결과 불명(UNKNOWN) 재조회.
 * 담당: 민섭. 규칙: 주문당 승인 1건 · 진행 중 시도 1건(DB 유니크로 보장), PG 호출은 DB 트랜잭션 밖.
 * PaymentGatewayPort(approve/cancel/query)만 알고, PG사 확정 전에는 가짜 어댑터로 흐름을 완성한다.
 */
package com.coffeul.payment;
