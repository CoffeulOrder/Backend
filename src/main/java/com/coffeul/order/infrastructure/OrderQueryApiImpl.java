package com.coffeul.order.infrastructure;

import com.coffeul.order.api.OrderQueryApi;
import com.coffeul.order.domain.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderQueryApiImpl implements OrderQueryApi {

    private static final List<String> TERMINAL_STATUSES = List.of(
            Order.STATUS_COMPLETED, Order.STATUS_CANCELED, Order.STATUS_REJECTED, Order.STATUS_EXPIRED);

    private final OrderRepository orderRepository;

    public OrderQueryApiImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public long countActiveOrders(Long memberId) {
        return orderRepository.countByMemberIdAndStatusNotIn(memberId, TERMINAL_STATUSES);
    }
}
