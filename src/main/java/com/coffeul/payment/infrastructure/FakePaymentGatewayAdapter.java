package com.coffeul.payment.infrastructure;

import com.coffeul.payment.api.PaymentAttemptView;
import com.coffeul.payment.api.PaymentPreparationApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PG사 확정 전 가짜 어댑터 (rules.py CONFIG_KEYS: PAYMENT_GATEWAY=fake|toss, PG사 확정 전 기본 fake).
 * 실제 카드 결제·시크릿과 무관한 값만 만든다 — 절대 진짜 PG 키를 흉내 내지 않는다.
 */
@Component
public class FakePaymentGatewayAdapter implements PaymentPreparationApi {

    private final String gateway;

    public FakePaymentGatewayAdapter(@Value("${coffeul.payment.gateway:fake}") String gateway) {
        this.gateway = gateway;
    }

    @Override
    public PaymentAttemptView prepareFirstAttempt(String orderCode, int amount) {
        if (!"fake".equals(gateway)) {
            throw new UnsupportedOperationException(
                    "PAYMENT_GATEWAY=" + gateway + " 어댑터는 아직 없음 — PG사 확정 후 구현");
        }
        int attemptNo = 1;
        return new PaymentAttemptView(attemptNo, "FAKE", orderCode + "-" + attemptNo, "fake_client_key_local_only");
    }
}
