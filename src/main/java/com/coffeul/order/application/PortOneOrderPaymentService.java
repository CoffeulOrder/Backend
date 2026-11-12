package com.coffeul.order.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.member.api.MemberStatusApi;
import com.coffeul.order.domain.Order;
import com.coffeul.order.infrastructure.OrderRepository;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.api.PortOnePaymentApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;

@Service
@ConditionalOnProperty(name = "coffeul.payment.gateway", havingValue = "portone")
public class PortOneOrderPaymentService {
    private final OrderRepository orders;
    private final MemberStatusApi members;
    private final PortOnePaymentApi payments;
    private final OrderPaymentPlacement placement;
    private final TransactionTemplate transaction;

    PortOneOrderPaymentService(OrderRepository orders, MemberStatusApi members, PortOnePaymentApi payments,
                               OrderPaymentPlacement placement, TransactionTemplate transaction) {
        this.orders = orders;
        this.members = members;
        this.payments = payments;
        this.placement = placement;
        this.transaction = transaction;
    }

    public record Result(Order order, int amount, String method, Instant approvedAt) { }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result confirm(Long memberId, Long orderId, String paymentId) {
        var order = orders.findPaymentConfirmationStateByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        requireActiveMember(memberId);
        var attempt = payments.findAttempt(orderId, paymentId);
        if (!attempt.approved()) {
            requirePaymentWindow(order.getStatus(), order.getExpiresAt(), Instant.now());
        }
        return confirmAttempt(memberId, attempt);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void confirmFromWebhook(String storeId, String paymentId) {
        var attempt = payments.findAttemptByPaymentId(paymentId);
        if (attempt.isEmpty()) {
            return;
        }
        if (!storeId.equals(attempt.get().storeId())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH);
        }
        confirmAttempt(null, attempt.get());
    }

    private Result confirmAttempt(Long customerId, PortOnePaymentApi.Attempt attempt) {
        // 외부 조회가 지연돼도 주문 행을 점유하지 않는다.
        var approval = attempt.approved() ? null : payments.verify(attempt);
        return transaction.execute(status -> finalizePayment(customerId, attempt, approval));
    }

    private Result finalizePayment(Long customerId, PortOnePaymentApi.Attempt inspected,
                                   PortOnePaymentApi.Approval approval) {
        long orderId = inspected.orderId();
        var order = orders.findByIdForUpdate(orderId)
                .filter(found -> customerId == null || found.getMemberId().equals(customerId))
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        requireActiveMember(order.getMemberId());
        var attempt = payments.lockAttempt(orderId, inspected.paymentId());
        if (attempt.amount() != order.getTotalAmount() || attempt.amount() != inspected.amount()) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        if (attempt.id() != inspected.id() || !Objects.equals(attempt.storeId(), inspected.storeId())
                || !Objects.equals(attempt.channelKey(), inspected.channelKey())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH);
        }
        if (attempt.approved() && !Order.STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
            return new Result(order, attempt.amount(), attempt.method(), attempt.approvedAt());
        }
        Instant now = Instant.now();
        requirePaymentWindow(order.getStatus(), order.getExpiresAt(), now);
        if (approval == null || !attempt.awaitingConfirmation()) {
            throw new BusinessException(PaymentErrorCode.INVALID_STATE);
        }
        placement.validateAvailability(order);
        payments.recordApproval(attempt, approval);
        placement.place(order, now, "PORTONE_PAYMENT_CONFIRMED");
        return new Result(order, attempt.amount(), approval.method(), approval.paidAt());
    }

    private void requirePaymentWindow(String status, Instant expiresAt, Instant now) {
        if (Order.STATUS_EXPIRED.equals(status)) {
            throw new BusinessException(PaymentErrorCode.ORDER_EXPIRED);
        }
        if (!Order.STATUS_PENDING_PAYMENT.equals(status)) {
            throw new BusinessException(PaymentErrorCode.INVALID_STATE);
        }
        if (!expiresAt.isAfter(now)) {
            throw new BusinessException(PaymentErrorCode.ORDER_EXPIRED);
        }
    }

    private void requireActiveMember(Long memberId) {
        if (!members.isActive(memberId)) {
            throw new BusinessException(OrderErrorCode.MEMBER_NOT_USABLE);
        }
    }
}
