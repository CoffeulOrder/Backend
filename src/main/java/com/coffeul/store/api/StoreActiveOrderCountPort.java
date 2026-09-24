package com.coffeul.store.api;

/**
 * MS-8(영업 상태 변경) 마감 확인용 역방향 포트 — store가 정의하고 order가 구현한다 (REQ-ST-004).
 * order가 이미 store.api를 부르고 있어서, store가 order.api를 직접 부르면 store ↔ order 순환 의존이 된다.
 * "처리 중"의 정의(접수~픽업 대기: REQUESTED·ACCEPTED·MAKING·READY)는 주문 모듈의 지식이라 여기서 정하지 않는다.
 */
public interface StoreActiveOrderCountPort {

    long countActiveOrders(Long storeId);
}
