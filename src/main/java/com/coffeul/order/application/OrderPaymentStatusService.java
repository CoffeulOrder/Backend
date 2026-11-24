package com.coffeul.order.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.member.api.MemberStatusApi;
import com.coffeul.order.domain.Order;
import com.coffeul.order.infrastructure.OrderRepository;
import com.coffeul.payment.api.PaymentStatusApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
public class OrderPaymentStatusService {
    private final OrderRepository orders;
    private final MemberStatusApi members;
    private final PaymentStatusApi payments;

    public OrderPaymentStatusService(OrderRepository orders, MemberStatusApi members, PaymentStatusApi payments) {
        this.orders = orders;
        this.members = members;
        this.payments = payments;
    }

    public record Result(long orderId, String orderStatus, Instant expiresAt, Short pickupNo,
                         boolean retryAvailable, Payment payment, PaymentStatusApi.Refund refund) { }
    public record Payment(int attemptNo, String pgProvider, String pgOrderId, String status, int amount,
                          String method, Instant approvedAt, Instant canceledAt) { }

    @Transactional(readOnly = true)
    public Result get(long memberId, long orderId) {
        var order = orders.findById(orderId).filter(found -> found.getMemberId().equals(memberId))
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!members.isActive(memberId)) throw new BusinessException(OrderErrorCode.MEMBER_NOT_USABLE);
        var payment = payments.findLatest(orderId).orElse(null);
        boolean retryAvailable = Order.STATUS_PENDING_PAYMENT.equals(order.getStatus())
                && order.getExpiresAt().isAfter(Instant.now()) && payment != null
                && "INICIS_V2".equals(payment.pgProvider()) && Set.of("READY", "FAILED").contains(payment.status());
        Payment view = payment == null ? null : new Payment(payment.attemptNo(), payment.pgProvider(),
                payment.pgOrderId(), payment.status(), payment.amount(), payment.method(), payment.approvedAt(), payment.canceledAt());
        return new Result(orderId, order.getStatus(), order.getExpiresAt(), order.getPickupNo(), retryAvailable,
                view, payment == null ? null : payment.refund());
    }
}
