package com.coffeul.order.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.member.api.MemberStatusApi;
import com.coffeul.order.domain.Order;
import com.coffeul.order.infrastructure.OrderLineRepository;
import com.coffeul.order.infrastructure.OrderRepository;
import com.coffeul.payment.api.PaymentAttemptView;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.api.PortOnePaymentApi;
import com.coffeul.payment.api.PortOnePaymentApi.RetryAttempt;
import com.coffeul.payment.api.PortOnePaymentApi.RetryState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "coffeul.payment.gateway", havingValue = "portone")
public class PortOnePaymentRetryService {
    private static final Set<String> PLACED = Set.of(Order.STATUS_REQUESTED, Order.STATUS_ACCEPTED,
            Order.STATUS_MAKING, Order.STATUS_READY, Order.STATUS_COMPLETED);
    private final OrderRepository orders;
    private final OrderLineRepository lines;
    private final MemberStatusApi members;
    private final PortOnePaymentApi payments;
    private final PortOneOrderPaymentService confirmation;
    private final OrderPaymentPlacement placement;
    private final TransactionTemplate transaction;

    public PortOnePaymentRetryService(OrderRepository orders, OrderLineRepository lines, MemberStatusApi members,
            PortOnePaymentApi payments, PortOneOrderPaymentService confirmation, OrderPaymentPlacement placement,
            TransactionTemplate transaction) {
        this.orders = orders;
        this.lines = lines;
        this.members = members;
        this.payments = payments;
        this.confirmation = confirmation;
        this.placement = placement;
        this.transaction = transaction;
    }

    public record Checkout(int attemptNo, String pgProvider, String pgOrderId, String orderName, int amount,
                           PaymentAttemptView.PortOneCheckout portOne) { }
    public record Result(long orderId, String status, String action, Instant expiresAt, Short pickupNo,
                         Checkout payment) { }
    private record Inspected(Order order, RetryAttempt payment) { }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result retry(long memberId, long orderId) {
        var inspected = transaction.execute(status -> inspect(memberId, orderId));
        if (PLACED.contains(inspected.order().getStatus())) return viewOrder(inspected.order());
        var attempt = inspected.payment().attempt();
        RetryState state = payments.inspectRetry(attempt);
        if (state == RetryState.PAID) {
            // 승인 검증·주문 접수·만료 보상은 기존 경로와 똑같이 처리한다.
            return viewOrder(confirmation.confirm(memberId, orderId, attempt.paymentId()).order());
        }
        if (state == RetryState.WAITING) throw new BusinessException(PaymentErrorCode.RESULT_PENDING);
        if (state == RetryState.CANCELED) throw new BusinessException(PaymentErrorCode.INVALID_STATE);
        return transaction.execute(status -> resume(memberId, orderId, inspected.payment(), state));
    }

    private Inspected inspect(long memberId, long orderId) {
        Order order = lockOrder(memberId, orderId);
        if (PLACED.contains(order.getStatus())) return new Inspected(order, null);
        requireOpenWindow(order);
        var attempt = payments.lockCurrentAttempt(orderId);
        if (!Set.of("READY", "UNKNOWN", "FAILED").contains(attempt.attempt().status())) {
            throw new BusinessException(PaymentErrorCode.INVALID_STATE);
        }
        if (attempt.attempt().amount() != order.getTotalAmount()) throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        return new Inspected(order, attempt);
    }

    private Result resume(long memberId, long orderId, RetryAttempt inspected, RetryState state) {
        Order order = lockOrder(memberId, orderId);
        if (PLACED.contains(order.getStatus())) return viewOrder(order);
        requireOpenWindow(order);
        var current = payments.lockCurrentAttempt(orderId);
        var attempt = current.attempt();
        var previous = inspected.attempt();
        if (attempt.id() != previous.id() || !attempt.paymentId().equals(previous.paymentId())
                || !Objects.equals(attempt.storeId(), previous.storeId())
                || !Objects.equals(attempt.channelKey(), previous.channelKey())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH);
        }
        if (attempt.amount() != order.getTotalAmount() || attempt.amount() != previous.amount()) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        if (!Set.of("READY", "UNKNOWN", "FAILED").contains(attempt.status())) {
            throw new BusinessException(PaymentErrorCode.INVALID_STATE);
        }
        if (!"READY".equals(attempt.status()) && state != RetryState.FAILED) {
            throw new BusinessException(PaymentErrorCode.RESULT_PENDING);
        }
        placement.validateAvailability(order);
        if (!"READY".equals(attempt.status())) payments.resumeFailedAttempt(attempt);
        var items = lines.findByOrderId(orderId);
        if (items.isEmpty()) throw new BusinessException(PaymentErrorCode.INVALID_STATE);
        String name = items.get(0).getMenuNameSnapshot() + (items.size() == 1 ? "" : " 외 " + (items.size() - 1) + "건");
        var checkout = new Checkout(current.attemptNo(), "INICIS_V2", attempt.paymentId(), name, attempt.amount(),
                new PaymentAttemptView.PortOneCheckout(attempt.storeId(), attempt.channelKey(), attempt.paymentId(), "CURRENCY_KRW"));
        return new Result(orderId, order.getStatus(), "OPEN_CHECKOUT", order.getExpiresAt(), null, checkout);
    }

    private Order lockOrder(long memberId, long orderId) {
        var order = orders.findByIdForUpdate(orderId).filter(found -> found.getMemberId().equals(memberId))
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!members.isActive(memberId)) throw new BusinessException(OrderErrorCode.MEMBER_NOT_USABLE);
        return order;
    }

    private void requireOpenWindow(Order order) {
        if (Order.STATUS_EXPIRED.equals(order.getStatus())
                || (Order.STATUS_PENDING_PAYMENT.equals(order.getStatus()) && !order.getExpiresAt().isAfter(Instant.now()))) {
            throw new BusinessException(PaymentErrorCode.ORDER_EXPIRED);
        }
        if (!Order.STATUS_PENDING_PAYMENT.equals(order.getStatus())) throw new BusinessException(PaymentErrorCode.INVALID_STATE);
    }

    private Result viewOrder(Order order) {
        return new Result(order.getId(), order.getStatus(), "VIEW_ORDER", order.getExpiresAt(), order.getPickupNo(), null);
    }
}
