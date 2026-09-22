package com.coffeul.order.infrastructure;

import com.coffeul.member.api.ActiveOrderCountPort;
import com.coffeul.order.api.OrderQueryApi;
import org.springframework.stereotype.Component;

/**
 * member가 정의한 역방향 포트를 order가 구현한다 (SM-7의 U005 판정).
 *
 * <p>"진행 중 주문"의 정의는 한 군데에만 두려고 {@link OrderQueryApi}에 그대로 위임한다 —
 * 종료 상태 목록이 바뀔 때 고칠 곳이 두 군데가 되면 탈퇴 판정만 조용히 어긋난다.
 */
@Component
public class ActiveOrderCountAdapter implements ActiveOrderCountPort {

    private final OrderQueryApi orderQueryApi;

    public ActiveOrderCountAdapter(OrderQueryApi orderQueryApi) {
        this.orderQueryApi = orderQueryApi;
    }

    @Override
    public long countActiveOrders(Long memberId) {
        return orderQueryApi.countActiveOrders(memberId);
    }
}
