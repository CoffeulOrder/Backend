package com.coffeul.order.application;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.api.StaffCredentialPort;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.order.domain.InvalidOrderTransitionException;
import com.coffeul.order.domain.Order;
import com.coffeul.order.domain.OrderRejectReason;
import com.coffeul.order.domain.OrderStatusHistory;
import com.coffeul.order.infrastructure.OrderRepository;
import com.coffeul.order.infrastructure.OrderStatusHistoryRepository;
import com.coffeul.payment.api.PaymentCancellationApi;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.store.api.StoreAccessPolicy;
import com.coffeul.store.api.StoreErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class OrderRejectionService {

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final StoreAccessPolicy access;
    private final StaffCredentialPort staffAccounts;
    private final PaymentCancellationApi payments;

    public OrderRejectionService(OrderRepository orders, OrderStatusHistoryRepository history,
                                 StoreAccessPolicy access, StaffCredentialPort staffAccounts,
                                 PaymentCancellationApi payments) {
        this.orders = orders;
        this.history = history;
        this.access = access;
        this.staffAccounts = staffAccounts;
        this.payments = payments;
    }

    public record Result(Order order, PaymentCancellationApi.Cancellation refund) {
    }

    @Transactional
    public Result reject(AuthUser staff, Long orderId, String reasonCode, String reasonDetail) {
        requireStaff(staff);
        if (reasonCode == null || reasonCode.isBlank()) {
            throw invalid("reasonCode", "거절 사유를 선택해 주세요.");
        }
        OrderRejectReason reason;
        try {
            reason = OrderRejectReason.valueOf(reasonCode);
        } catch (IllegalArgumentException ex) {
            throw invalid("reasonCode", "거절 사유를 다시 선택해 주세요.");
        }
        String detail = reasonDetail == null || reasonDetail.isBlank() ? null : reasonDetail.strip();
        if (reason == OrderRejectReason.OTHER && detail == null) {
            throw invalid("reasonDetail", "기타 사유를 입력해 주세요.");
        }

        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        // MySQL REPEATABLE READ의 조회 스냅샷은 주문 잠금 뒤에 시작해야 동시 거절의 환불도 보인다.
        requireActiveStaff(staff);
        access.check(staff, order.getStoreId());
        if (Order.STATUS_REJECTED.equals(order.getStatus())) {
            if (!reasonCode.equals(order.getRejectReasonCode()) || !Objects.equals(detail, order.getRejectReasonDetail())) {
                throw invalidTransition(order);
            }
            var refund = payments.findByOrderId(orderId)
                    .orElseThrow(() -> new BusinessException(PaymentErrorCode.ATTEMPT_NOT_FOUND));
            return new Result(order, refund);
        }
        try {
            order.reject(Instant.now(), reason, detail);
        } catch (InvalidOrderTransitionException ex) {
            throw invalidTransition(order);
        }
        var refund = payments.requestStaffRejection(orderId, order.getTotalAmount(), "매장 거절: " + reasonCode);
        orders.save(order);
        history.save(OrderStatusHistory.transitioned(orderId, Order.STATUS_REQUESTED, Order.STATUS_REJECTED,
                "STAFF", staff.id(), detail == null ? reasonCode : detail));
        return new Result(order, refund);
    }

    private void requireStaff(AuthUser staff) {
        if (staff.type() != AuthUser.SubjectType.STAFF
                || (staff.role() != AuthUser.Role.STAFF && staff.role() != AuthUser.Role.OWNER)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private void requireActiveStaff(AuthUser staff) {
        staffAccounts.findById(staff.id())
                .filter(account -> account.active() && account.role().equals(staff.role().name())
                        && Objects.equals(account.merchantId(), staff.merchantId()))
                .orElseThrow(() -> new BusinessException(StoreErrorCode.FORBIDDEN));
    }

    private BusinessException invalid(String field, String reason) {
        return new BusinessException(CommonErrorCode.VALIDATION_FAILED, reason,
                List.of(new ApiResponse.FieldError(field, reason)));
    }

    private BusinessException invalidTransition(Order order) {
        return BusinessException.withData(OrderErrorCode.INVALID_TRANSITION,
                OrderErrorCode.INVALID_TRANSITION.message(), Map.of("currentStatus", order.getStatus()));
    }
}
