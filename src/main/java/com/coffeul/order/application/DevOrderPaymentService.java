package com.coffeul.order.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.member.api.MemberStatusApi;
import com.coffeul.order.domain.Order;
import com.coffeul.order.infrastructure.OrderRepository;
import com.coffeul.payment.api.FakePaymentApi;
import com.coffeul.payment.api.PaymentErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/** 로컬/개발 서버에서만 사용하는 가짜 결제 확정. 실결제 승인(MS-27)이 아니다. */
@Service
@Profile("dev & !prod & !production")
@ConditionalOnProperty(name = "coffeul.dev.payment-flow-enabled", havingValue = "true")
@ConditionalOnProperty(name = "coffeul.payment.gateway", havingValue = "fake", matchIfMissing = true)
public class DevOrderPaymentService {
    private final OrderRepository orders;
    private final MemberStatusApi members;
    private final FakePaymentApi payments;
    private final OrderPaymentPlacement placement;

    DevOrderPaymentService(OrderRepository orders, MemberStatusApi members, FakePaymentApi payments,
                           OrderPaymentPlacement placement) {
        this.orders = orders;
        this.members = members;
        this.payments = payments;
        this.placement = placement;
    }

    public record Result(Order order, FakePaymentApi.State payment) { }

    @Transactional
    public Result confirm(Long memberId, Long orderId, String pgOrderId) {
        Order order = orders.findByIdForUpdate(orderId)
                .filter(found -> found.getMemberId().equals(memberId))
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!members.isActive(memberId)) {
            throw new BusinessException(OrderErrorCode.MEMBER_NOT_USABLE);
        }
        FakePaymentApi.State state = payments.findAttempt(orderId, pgOrderId);
        // 승인 뒤 수락·제조 등이 진행됐어도 재전송은 현재 주문 상태를 그대로 돌려준다.
        if ("APPROVED".equals(state.status()) && !Order.STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
            return new Result(order, state);
        }
        if (!Order.STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
            throw BusinessException.withData(OrderErrorCode.INVALID_TRANSITION,
                    OrderErrorCode.INVALID_TRANSITION.message(), Map.of("currentStatus", order.getStatus()));
        }
        Instant now = Instant.now();
        if (!order.getExpiresAt().isAfter(now)) {
            throw new BusinessException(PaymentErrorCode.ORDER_EXPIRED);
        }
        placement.validateAvailability(order);
        state = payments.approve(orderId, pgOrderId, order.getTotalAmount(), now);
        placement.place(order, now, "DEV_FAKE_PAYMENT");
        return new Result(order, state);
    }
}
