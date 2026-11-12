package com.coffeul.payment.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.api.PortOnePaymentApi;
import com.coffeul.payment.infrastructure.PortOnePaymentClient;
import com.coffeul.payment.infrastructure.PortOnePaymentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@ConditionalOnProperty(name = "coffeul.payment.gateway", havingValue = "portone")
public class PortOnePaymentVerificationService implements PortOnePaymentApi {
    private final PortOnePaymentRepository repository;
    private final PortOnePaymentClient client;

    public PortOnePaymentVerificationService(PortOnePaymentRepository repository, PortOnePaymentClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Override
    public Attempt findAttempt(Long orderId, String paymentId) {
        return repository.find(orderId, paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ATTEMPT_NOT_FOUND));
    }

    @Override
    public Optional<Attempt> findAttemptByPaymentId(String paymentId) {
        return repository.findByPaymentId(paymentId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Attempt lockAttempt(Long orderId, String paymentId) {
        return repository.findForUpdate(orderId, paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ATTEMPT_NOT_FOUND));
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public Approval verify(Attempt attempt) {
        if (!attempt.awaitingConfirmation()) {
            throw new BusinessException(PaymentErrorCode.INVALID_STATE);
        }
        if (!StringUtils.hasText(attempt.storeId()) || !StringUtils.hasText(attempt.channelKey())) {
            throw new BusinessException(PaymentErrorCode.SETUP_NOT_READY);
        }
        var payment = client.getPaidPayment(attempt.paymentId(), attempt.storeId());
        if (!attempt.paymentId().equals(payment.paymentId()) || !attempt.storeId().equals(payment.storeId())
                || !attempt.channelKey().equals(payment.channelKey()) || !"KRW".equals(payment.currency())
                || !"V2".equals(payment.version()) || !PortOnePaymentRepository.PROVIDER.equals(payment.provider())) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH);
        }
        if (attempt.amount() != payment.totalAmount()) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        if (payment.transactionId().length() > 200 || (payment.method() != null && payment.method().length() > 30)) {
            throw new BusinessException(PaymentErrorCode.LOOKUP_UNAVAILABLE);
        }
        return new Approval(payment.transactionId(), payment.method(), payment.paidAt());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordApproval(Attempt attempt, Approval approval) {
        try {
            if (repository.approve(attempt.id(), approval.transactionId(), approval.method(), approval.paidAt()) != 1) {
                throw new BusinessException(PaymentErrorCode.INVALID_STATE);
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_CONTEXT_MISMATCH);
        }
    }
}
