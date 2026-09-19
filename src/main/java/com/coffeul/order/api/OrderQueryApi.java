package com.coffeul.order.api;

/** 다른 모듈이 회원의 활성(종료되지 않은) 주문 수를 확인할 때 쓰는 포트 (예: SM-7 탈퇴 시 U005 판정 — data.activeOrderCount). */
public interface OrderQueryApi {

    long countActiveOrders(Long memberId);
}
