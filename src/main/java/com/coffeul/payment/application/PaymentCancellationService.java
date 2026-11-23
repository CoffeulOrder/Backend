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
        var payment = repository.lockPayment(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ATTEMPT_NOT_FOUND));
        if (amount <= 0 || amount != payment.amount()) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        if (!gateway.supports(payment.provider())) {
            throw new BusinessException(PaymentErrorCode.SETUP_NOT_READY);
        }
        var existing = repository.findByOrderId(orderId);
        if (existing.isPresent()) {
            if (existing.get().amount() != amount) {
                throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
            }
            return existing.get();
        }
        if (!"APPROVED".equals(payment.status())) {
            throw new BusinessException(PaymentErrorCode.INVALID_STATE);
        }
        return repository.insert(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cancellation> findByOrderId(Long orderId) {
        return repository.findByOrderId(orderId);
    }
}
