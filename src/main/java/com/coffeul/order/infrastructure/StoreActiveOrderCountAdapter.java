package com.coffeul.order.infrastructure;

import com.coffeul.order.domain.Order;
import com.coffeul.store.api.StoreActiveOrderCountPort;
import org.springframework.stereotype.Component;

import java.util.List;

/** store가 정의한 역방향 포트를 order가 구현한다 (MS-8 마감 확인, REQ-ST-004). */
@Component
public class StoreActiveOrderCountAdapter implements StoreActiveOrderCountPort {

    /** "처리 중" = 접수~픽업 대기. 결제 대기(PENDING_PAYMENT)는 아직 매장에 접수된 게 아니라 뺀다. */
    private static final List<String> IN_PROGRESS_STATUSES = List.of(
            Order.STATUS_REQUESTED, Order.STATUS_ACCEPTED, Order.STATUS_MAKING, Order.STATUS_READY);

    private final OrderRepository orderRepository;

    public StoreActiveOrderCountAdapter(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public long countActiveOrders(Long storeId) {
        return orderRepository.countByStoreIdAndStatusIn(storeId, IN_PROGRESS_STATUSES);
    }
}
