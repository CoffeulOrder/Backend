package com.coffeul.order.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.member.api.MemberStatusApi;
import com.coffeul.order.domain.InvalidOrderTransitionException;
import com.coffeul.order.domain.Order;
import com.coffeul.order.domain.OrderStatusHistory;
import com.coffeul.order.infrastructure.OrderRepository;
import com.coffeul.order.infrastructure.OrderStatusHistoryRepository;
import com.coffeul.payment.api.PaymentCancellationApi;
import com.coffeul.payment.api.PaymentErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class OrderCancelService {

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final MemberStatusApi members;
    private final PaymentCancellationApi payments;

    public OrderCancelService(OrderRepository orders, OrderStatusHistoryRepository history,
                              MemberStatusApi members, PaymentCancellationApi payments) {
        this.orders = orders;
        this.history = history;
        this.members = members;
        this.payments = payments;
    }

    public record Result(Order order, PaymentCancellationApi.Cancellation cancellation) {
    }

    @Transactional
    public Result cancel(Long memberId, Long orderId, String reason) {
        Order order = orders.findByIdForUpdate(orderId)
                .filter(found -> found.getMemberId().equals(memberId))
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!members.isActive(memberId)) {
            throw new BusinessException(OrderErrorCode.MEMBER_NOT_USABLE);
        }
        if (Order.STATUS_CANCELED.equals(order.getStatus())) {
            var cancellation = payments.findByOrderId(orderId)
                    .orElseThrow(() -> new BusinessException(PaymentErrorCode.ATTEMPT_NOT_FOUND));
            return new Result(order, cancellation);
        }
        try {
            order.cancel(Instant.now());
        } catch (InvalidOrderTransitionException ex) {
            throw BusinessException.withData(OrderErrorCode.INVALID_TRANSITION,
                    OrderErrorCode.INVALID_TRANSITION.message(), Map.of("currentStatus", ex.currentStatus()));
        }

        // 환불 요청과 취소 상태를 함께 저장한 뒤, 별도 작업이 PG에 전액 취소를 요청한다.
        var cancellation = payments.requestCustomerCancellation(orderId, order.getTotalAmount());
        orders.save(order);
        history.save(OrderStatusHistory.transitioned(orderId, Order.STATUS_REQUESTED, Order.STATUS_CANCELED,
                "CUSTOMER", memberId, reason));
        return new Result(order, cancellation);
    }
}
