package com.coffeul.member.api;

/**
 * 탈퇴 가능 여부를 판정할 때 쓰는 "이 회원의 진행 중 주문 수" 역방향 포트 (SM-7 · REQ-U-007의 U005).
 *
 * <p>member가 정의하고 order가 구현한다 — member가 order.api를 직접 부르면 순환 의존이 된다.
 * order는 이미 주문 생성(MS-16)에서 {@link MemberStatusApi}로 계정 상태를 확인하느라 member를 부르고 있어서,
 * member가 order를 부르는 순간 member ↔ order 사이클이 생기고 Spring Modulith가 빌드를 깬다.
 * verification이 {@code MemberAccountPort}를 정의하고 member가 구현하는 것과 같은 방식이다.
 *
 * <p>"진행 중"의 정의(종료 상태 4개 제외)는 주문 모듈이 갖고 있어야 할 지식이라 여기서 정하지 않는다.
 * 구현은 {@code order.api.OrderQueryApi#countActiveOrders}에 그대로 위임한다.
 */
public interface ActiveOrderCountPort {

    long countActiveOrders(Long memberId);
}
