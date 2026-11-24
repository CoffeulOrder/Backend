package com.coffeul.order.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.member.api.MemberStatusApi;
import com.coffeul.order.domain.Order;
import com.coffeul.order.domain.OrderRejectReason;
import com.coffeul.order.domain.OrderStatusHistory;
import com.coffeul.order.infrastructure.OrderRepository;
import com.coffeul.order.infrastructure.OrderStatusHistoryRepository;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.api.PaymentCancellationApi;
import com.coffeul.payment.api.PortOnePaymentApi;
import com.coffeul.store.api.StoreErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "coffeul.payment.gateway", havingValue = "portone")
public class PortOneOrderPaymentService {
    private final OrderRepository orders;
    private final MemberStatusApi members;
    private final PortOnePaymentApi payments;
    private final OrderPaymentPlacement placement;
    private final TransactionTemplate transaction;
    private final PaymentCancellationApi cancellations;
    private final OrderExpiryJob expiry;
    private final OrderStatusHistoryRepository histories;

    PortOneOrderPaymentService(OrderRepository orders, MemberStatusApi members, PortOnePaymentApi payments,
                               OrderPaymentPlacement placement, TransactionTemplate transaction,
                               PaymentCancellationApi cancellations, OrderExpiryJob expiry,
                               OrderStatusHistoryRepository histories) {
        this.orders = orders;
        this.members = members;
        this.payments = payments;
        this.placement = placement;
        this.transaction = transaction;
        this.cancellations = cancellations;
        this.expiry = expiry;
        this.histories = histories;
    }

    public record Result(Order order, int amount, String method, Instant approvedAt,
                         PaymentCancellationApi.Cancellation refund) { }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result confirm(Long memberId, Long orderId, String paymentId) {
        var order = orders.findPaymentConfirmationStateByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        requireActiveMember(memberId);
        var attempt = payments.findAttempt(orderId, paymentId);
        if (!attempt.approved() && !"CANCELED".equals(attempt.status())
                && !isExpired(order.getStatus(), order.getExpiresAt(), Instant.now())) {
            requirePaymentWindow(order.getStatus(), order.getExpiresAt(), Instant.now());
        }
        var result = confirmAttempt(memberId, attempt);
        if (Order.STATUS_EXPIRED.equals(result.order().getStatus())) {
            // 환불 요청을 커밋한 뒤 실패 응답을 만든다. 트랜잭션 안에서 던지면 환불도 롤백된다.
            throw BusinessException.withData(PaymentErrorCode.ORDER_EXPIRED,
                    "주문이 만료됐어요. 환불 상태를 확인해 주세요.",
                    Map.of("orderId", orderId, "status", Order.STATUS_EXPIRED,
                            "refundStatus", result.refund().status(), "refundAmount", result.refund().amount()));
        }
        if (Order.STATUS_REJECTED.equals(result.order().getStatus()) && result.refund() != null) {
            throw BusinessException.withData(PaymentErrorCode.ORDER_NOT_ACCEPTED,
                    PaymentErrorCode.ORDER_NOT_ACCEPTED.message(),
                    Map.of("orderId", orderId, "status", Order.STATUS_REJECTED,
                            "refundStatus", result.refund().status(), "refundAmount", result.refund().amount()));
        }
        return result;
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void recoverExpiredPayment(long orderId, String paymentId) {
        var order = orders.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!isExpired(order.getStatus(), order.getExpiresAt(), Instant.now())) {
            return;
        }
        confirmAttempt(null, payments.findAttempt(orderId, paymentId));
    }

    private Result confirmAttempt(Long customerId, PortOnePaymentApi.Attempt attempt) {
        // 외부 조회가 지연돼도 주문 행을 점유하지 않는다.
        var approval = attempt.approved() || "CANCELED".equals(attempt.status()) ? null : payments.verify(attempt);
        return transaction.execute(status -> finalizePayment(customerId, attempt, approval));
    }

    private Result finalizePayment(Long customerId, PortOnePaymentApi.Attempt inspected,
                                   PortOnePaymentApi.Approval approval) {
        long orderId = inspected.orderId();
        var order = orders.findByIdForUpdate(orderId)
                .filter(found -> customerId == null || found.getMemberId().equals(customerId))
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        var attempt = payments.lockAttempt(orderId, inspected.paymentId());
        if (attempt.amount() != order.getTotalAmount() || attempt.amount() != inspected.amount()) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        if (attempt.id() != inspected.id() || !Objects.equals(attempt.storeId(), inspected.storeId())
                || !Objects.equals(attempt.channelKey(), inspected.channelKey())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH);
        }
        Instant now = Instant.now();
        if (isExpired(order.getStatus(), order.getExpiresAt(), now)) {
            return refundExpiredPayment(order, attempt, approval, now);
        }
        if (Order.STATUS_REJECTED.equals(order.getStatus())
                && (attempt.approved() || "CANCELED".equals(attempt.status()))) {
            var refund = cancellations.findByOrderId(orderId)
                    .orElseThrow(() -> new BusinessException(PaymentErrorCode.INVALID_STATE));
            return new Result(order, attempt.amount(), attempt.method(), attempt.approvedAt(), refund);
        }
        if (attempt.approved() && !Order.STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
            return new Result(order, attempt.amount(), attempt.method(), attempt.approvedAt(), null);
        }
        requirePaymentWindow(order.getStatus(), order.getExpiresAt(), now);
        if (approval == null || !attempt.awaitingConfirmation()) {
            throw new BusinessException(PaymentErrorCode.INVALID_STATE);
        }
        try {
            requireActiveMember(order.getMemberId());
            placement.validateAvailability(order);
        } catch (BusinessException error) {
            var rejection = rejectionFor(error);
            if (rejection == null) throw error;
            return rejectPaidPayment(order, attempt, approval, now, rejection);
        }
        payments.recordApproval(attempt, approval);
        placement.place(order, now, "PORTONE_PAYMENT_CONFIRMED");
        return new Result(order, attempt.amount(), approval.method(), approval.paidAt(), null);
    }

    private record Rejection(OrderRejectReason reason, String detail) { }

    private Rejection rejectionFor(BusinessException error) {
        var code = error.errorCode();
        if (code == StoreErrorCode.NOT_ACCEPTING_ORDERS) {
            return new Rejection(OrderRejectReason.STORE_CLOSING, "결제 확인 중 매장이 주문 접수를 마감했어요.");
        }
        if (code == OrderErrorCode.SOLD_OUT) {
            return new Rejection(OrderRejectReason.SOLD_OUT, "결제 확인 중 주문한 메뉴가 품절됐어요.");
        }
        if (code == OrderErrorCode.INVALID_OPTION_SELECTION || code == OrderErrorCode.MENU_NOT_FOUND) {
            return new Rejection(OrderRejectReason.OTHER, "결제 확인 중 주문한 메뉴 또는 옵션을 제공할 수 없게 됐어요.");
        }
        if (code == OrderErrorCode.MEMBER_NOT_USABLE) {
            return new Rejection(OrderRejectReason.OTHER, "결제 확인 중 계정 상태가 변경되어 주문을 접수하지 못했어요.");
        }
        if (code == StoreErrorCode.NOT_FOUND) {
            return new Rejection(OrderRejectReason.OTHER, "결제 확인 중 매장 정보를 확인할 수 없어 주문을 접수하지 못했어요.");
        }
        return null;
    }

    private Result rejectPaidPayment(Order order, PortOnePaymentApi.Attempt attempt,
                                     PortOnePaymentApi.Approval approval, Instant now, Rejection rejection) {
        payments.recordApproval(attempt, approval);
        order.rejectBeforePlacement(now, rejection.reason(), rejection.detail());
        orders.save(order);
        histories.save(OrderStatusHistory.transitioned(order.getId(), Order.STATUS_PENDING_PAYMENT,
                Order.STATUS_REJECTED, "SYSTEM", null, rejection.detail()));
        var refund = cancellations.requestUnplaceablePayment(order.getId(), order.getTotalAmount(),
                "주문 접수 불가: " + rejection.reason().name());
        return new Result(order, attempt.amount(), approval.method(), approval.paidAt(), refund);
    }

    private Result refundExpiredPayment(Order order, PortOnePaymentApi.Attempt attempt,
                                        PortOnePaymentApi.Approval approval, Instant now) {
        if (Order.STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
            expiry.expireOne(order.getId(), now);
        }
        if ("CANCELED".equals(attempt.status())) {
            var refund = cancellations.findByOrderId(order.getId())
                    .orElseThrow(() -> new BusinessException(PaymentErrorCode.INVALID_STATE));
            return new Result(order, attempt.amount(), attempt.method(), attempt.approvedAt(), refund);
        }
        if (!attempt.approved()) {
            if (approval == null || !attempt.awaitingConfirmation()) {
                throw new BusinessException(PaymentErrorCode.INVALID_STATE);
            }
            payments.recordApproval(attempt, approval);
        }
        var refund = cancellations.requestExpiredPayment(order.getId(), order.getTotalAmount());
        return new Result(order, attempt.amount(), approval == null ? attempt.method() : approval.method(),
                approval == null ? attempt.approvedAt() : approval.paidAt(), refund);
    }

    private boolean isExpired(String status, Instant expiresAt, Instant now) {
        return Order.STATUS_EXPIRED.equals(status)
                || (Order.STATUS_PENDING_PAYMENT.equals(status) && !expiresAt.isAfter(now));
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
