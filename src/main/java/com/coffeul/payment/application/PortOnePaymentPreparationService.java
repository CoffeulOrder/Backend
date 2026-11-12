package com.coffeul.payment.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.payment.api.PaymentAttemptView;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.api.PaymentPreparationApi;
import com.coffeul.payment.api.PortOneMerchantConfigPort;
import com.coffeul.payment.infrastructure.PortOnePaymentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Primary
@ConditionalOnProperty(name = "coffeul.payment.gateway", havingValue = "portone")
public class PortOnePaymentPreparationService implements PaymentPreparationApi {
    private final PortOneMerchantConfigPort configPort;
    private final PortOnePaymentRepository repository;

    public PortOnePaymentPreparationService(PortOneMerchantConfigPort configPort, PortOnePaymentRepository repository) {
        this.configPort = configPort;
        this.repository = repository;
    }

    @Override
    public PaymentAttemptView prepareFirstAttempt(Long orderId, Long storeId, String orderCode, int amount) {
        if (amount <= 0) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        var config = configPort.findActiveInicisConfig(storeId)
                .filter(value -> StringUtils.hasText(value.portOneStoreId()) && StringUtils.hasText(value.channelKey()))
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.SETUP_NOT_READY));
        String paymentId = orderCode + "-1";
        repository.prepare(orderId, storeId, config, paymentId, amount);
        return checkout(paymentId, config.portOneStoreId(), config.channelKey());
    }

    @Override
    public PaymentAttemptView replayFirstAttempt(Long orderId, Long storeId, String orderCode, int amount) {
        String paymentId = orderCode + "-1";
        var attempt = repository.find(orderId, paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ATTEMPT_NOT_FOUND));
        if (attempt.amount() != amount) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        return checkout(paymentId, attempt.storeId(), attempt.channelKey());
    }

    private PaymentAttemptView checkout(String paymentId, String storeId, String channelKey) {
        var portOne = new PaymentAttemptView.PortOneCheckout(storeId, channelKey, paymentId, "CURRENCY_KRW");
        return new PaymentAttemptView(1, PortOnePaymentRepository.PROVIDER, paymentId, null, portOne);
    }
}
