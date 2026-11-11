package com.coffeul.payment;

import com.coffeul.common.error.BusinessException;
import com.coffeul.payment.api.FakeMerchantConfigPort;
import com.coffeul.payment.application.PersistentFakePaymentService;
import com.coffeul.payment.infrastructure.FakePaymentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PersistentFakePaymentServiceTest {
    @Test
    void realGatewayCannotUseSimulation() {
        var repository = mock(FakePaymentRepository.class);
        var config = mock(FakeMerchantConfigPort.class);
        var service = new PersistentFakePaymentService(repository, config, "portone");
        assertThatThrownBy(() -> service.prepareFirstAttempt(1L, 1L, "TEST-ORDER", 1000))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.approve(1L, "TEST-ORDER-1", 1000, Instant.now()))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository, config);
    }
}
