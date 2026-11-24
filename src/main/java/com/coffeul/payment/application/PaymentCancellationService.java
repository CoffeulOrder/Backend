package com.coffeul.payment.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.payment.api.PaymentCancellationApi;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.infrastructure.PaymentCancellationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PaymentCancellationService implements PaymentCancellationApi {
    private final PaymentCancellationRepository repository;
    private final PaymentCancellationGateway gateway;

    public PaymentCancellationService(PaymentCancellationRepository repository, PaymentCancellationGateway gateway) {
        this.repository = repository;
        this.gateway = gateway;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Cancellation requestCustomerCancellation(Long orderId, int amount) {
        return requestCancellation(orderId, amount, "CUSTOMER", "고객 요청");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Cancellation requestStaffRejection(Long orderId, int amount, String reason) {
        return requestCancellation(orderId, amount, "STAFF", reason);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Cancellation requestExpiredPayment(Long orderId, int amount) {
        return requestCancellation(orderId, amount, "SYSTEM", "주문 만료 후 결제 확인");
    }

    private Cancellation requestCancellation(Long orderId, int amount, String actor, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 200) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH);
        }
        var payment = repository.lockPayment(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ATTEMPT_NOT_FOUND));
        if (amount <= 0 || amount != payment.amount()) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        if (!gateway.supports(payment.provider())) {
            throw new BusinessException(PaymentErrorCode.SETUP_NOT_READY);
        }
        var existing = repository.findRequestByOrderId(orderId);
        if (existing.isPresent()) {
            var request = existing.get();
            if (request.cancellation().amount() != amount) {
                throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
            }
            if (!actor.equals(request.requestedByType()) || !reason.equals(request.reason())) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH);
            }
            return request.cancellation();
        }
        if (!"APPROVED".equals(payment.status())) {
            throw new BusinessException(PaymentErrorCode.INVALID_STATE);
        }
        return repository.insert(payment, actor, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cancellation> findByOrderId(Long orderId) {
        return repository.findByOrderId(orderId);
    }
}
