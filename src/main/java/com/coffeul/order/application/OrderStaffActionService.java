package com.coffeul.order.application;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.order.api.OrderReadyEvent;
import com.coffeul.order.domain.InvalidOrderTransitionException;
import com.coffeul.order.domain.Order;
import com.coffeul.order.domain.OrderStatusHistory;
import com.coffeul.order.infrastructure.OrderRepository;
import com.coffeul.order.infrastructure.OrderStatusHistoryRepository;
import com.coffeul.store.api.StoreAccessPolicy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * MS-22(수락) · MS-24(제조 시작) · MS-25(픽업 대기) · MS-26(픽업 완료) — 관리자앱 주문 카드 동작.
 * 네 API가 "권한 확인 → 이미 목표 상태면 그대로 200 → 전이 → 이력" 골격을 그대로 공유해서
 * {@link #transition}에 한 번만 두었다 (rules.py DOD: 매장용 상태 변경 API 공통 규칙).
 */
@Service
public class OrderStaffActionService {

    private static final String ACTOR_STAFF = "STAFF";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final StoreAccessPolicy storeAccessPolicy;
    private final ApplicationEventPublisher eventPublisher;

    public OrderStaffActionService(OrderRepository orderRepository,
                                    OrderStatusHistoryRepository orderStatusHistoryRepository,
                                    StoreAccessPolicy storeAccessPolicy,
                                    ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.storeAccessPolicy = storeAccessPolicy;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order accept(AuthUser staff, Long orderId) {
        return transition(staff, orderId, Order.STATUS_REQUESTED, Order.STATUS_ACCEPTED, Order::accept, null);
    }

    @Transactional
    public Order start(AuthUser staff, Long orderId) {
        return transition(staff, orderId, Order.STATUS_ACCEPTED, Order.STATUS_MAKING, Order::startMaking, null);
    }

    @Transactional
    public Order ready(AuthUser staff, Long orderId) {
        return transition(staff, orderId, Order.STATUS_MAKING, Order.STATUS_READY, Order::markReady,
                order -> eventPublisher.publishEvent(
                        new OrderReadyEvent(order.getId(), order.getStoreId(), order.getMemberId(), order.getPickupNo())));
    }

    @Transactional
    public Order complete(AuthUser staff, Long orderId) {
        return transition(staff, orderId, Order.STATUS_READY, Order.STATUS_COMPLETED, Order::complete, null);
    }

    private Order transition(AuthUser staff, Long orderId, String fromExpected, String toStatus,
                              BiConsumer<Order, Instant> applyTransition, Consumer<Order> afterTransition) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        storeAccessPolicy.check(staff, order.getStoreId());

        // 이미 목표 상태면 같은 응답으로 200 — 앱 타임아웃 후 재시도가 실패로 보이지 않게 (api.py MS-22~26 공통 memo).
        if (toStatus.equals(order.getStatus())) {
            return order;
        }

        try {
            applyTransition.accept(order, Instant.now());
        } catch (InvalidOrderTransitionException e) {
            throw BusinessException.withData(OrderErrorCode.INVALID_TRANSITION,
                    OrderErrorCode.INVALID_TRANSITION.message(), Map.of("currentStatus", e.currentStatus()));
        }
        orderRepository.save(order);
        orderStatusHistoryRepository.save(
                OrderStatusHistory.transitioned(order.getId(), fromExpected, toStatus, ACTOR_STAFF, staff.id(), null));

        if (afterTransition != null) {
            afterTransition.accept(order);
        }
        return order;
    }
}
