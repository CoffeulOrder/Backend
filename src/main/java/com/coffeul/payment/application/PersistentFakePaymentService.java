package com.coffeul.payment.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.payment.api.*;
import com.coffeul.payment.infrastructure.FakePaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Primary
@Profile("dev & !prod & !production")
@ConditionalOnProperty(name = "coffeul.dev.payment-flow-enabled", havingValue = "true")
public class PersistentFakePaymentService implements PaymentPreparationApi, FakePaymentApi {
    private final FakePaymentRepository repository;
    private final FakeMerchantConfigPort configPort;
    private final String gateway;

    public PersistentFakePaymentService(FakePaymentRepository repository, FakeMerchantConfigPort configPort,
                                        @Value("${coffeul.payment.gateway:fake}") String gateway) {
        this.repository = repository;
        this.configPort = configPort;
        this.gateway = gateway;
    }

    @Override
    @Transactional
    public PaymentAttemptView prepareFirstAttempt(Long orderId, Long storeId, String orderCode, int amount) {
        requireFakeGateway();
        var config = configPort.findActiveFakeConfig(storeId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.SETUP_NOT_READY));
        String pgOrderId = orderCode + "-1";
        repository.prepare(orderId, storeId, config.merchantPgId(), pgOrderId, amount);
        State state = findAttempt(orderId, pgOrderId);
        if (state.amount() != amount) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        // 공개 키도 가짜 문자열만 반환한다. 시드 외 설정값을 실제 PG 키로 잘못 노출하지 않는다.
        return new PaymentAttemptView(1, "FAKE", pgOrderId, "fake_client_key_local_only");
    }

    @Override
    public State findAttempt(Long orderId, String pgOrderId) {
        requireFakeGateway();
        return repository.findForUpdate(orderId, pgOrderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.ATTEMPT_NOT_FOUND));
    }

    @Override
    @Transactional
    public State approve(Long orderId, String pgOrderId, int expectedAmount, Instant now) {
        State state = findAttempt(orderId, pgOrderId);
        if (state.amount() != expectedAmount) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        if ("APPROVED".equals(state.status())) {
            return state;
        }
        if (!"READY".equals(state.status())) {
            throw new BusinessException(PaymentErrorCode.INVALID_STATE);
        }
        repository.approve(orderId, pgOrderId, now);
        return findAttempt(orderId, pgOrderId);
    }

    @Override
    public void expireReadyAttempts(Long orderId, Instant now) {
        repository.expire(orderId, now);
    }

    private void requireFakeGateway() {
        if (!"fake".equals(gateway)) {
            throw new BusinessException(PaymentErrorCode.SETUP_NOT_READY);
        }
    }
}
