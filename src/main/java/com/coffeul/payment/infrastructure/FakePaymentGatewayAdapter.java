package com.coffeul.payment.infrastructure;

import com.coffeul.common.error.BusinessException;
import com.coffeul.payment.api.PaymentAttemptView;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.api.PaymentPreparationApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 기본 가짜 어댑터. 응답용 결제 정보를 만든다.
 */
@Component
public class FakePaymentGatewayAdapter implements PaymentPreparationApi {

    private final String gateway;

    public FakePaymentGatewayAdapter(@Value("${coffeul.payment.gateway:fake}") String gateway) {
        this.gateway = gateway;
    }

    @Override
    public PaymentAttemptView prepareFirstAttempt(Long orderId, Long storeId, String orderCode, int amount) {
        if (!"fake".equalsIgnoreCase(gateway)) {
            throw new BusinessException(PaymentErrorCode.SETUP_NOT_READY);
        }
        int attemptNo = 1;
        return new PaymentAttemptView(attemptNo, "FAKE", orderCode + "-" + attemptNo, "fake_client_key_local_only");
    }
}
