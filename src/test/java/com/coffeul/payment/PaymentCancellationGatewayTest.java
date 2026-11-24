package com.coffeul.payment;

import com.coffeul.payment.application.PaymentCancellationGateway;
import com.coffeul.payment.infrastructure.PortOnePaymentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentCancellationGatewayTest {
    @Test
    void fakeRequiresDevAndExplicitOptInAndNeverProduction() {
        MockEnvironment env = new MockEnvironment();
        ObjectProvider<PortOnePaymentClient> provider = emptyProvider();
        var gateway = new PaymentCancellationGateway(env, provider);
        assertThat(gateway.supports("FAKE")).isFalse();
        env.setActiveProfiles("dev");
        assertThat(gateway.supports("FAKE")).isFalse();
        env.setProperty("coffeul.dev.payment-flow-enabled", "true");
        assertThat(gateway.supports("FAKE")).isTrue();
        env.setActiveProfiles("dev", "prod");
        assertThat(gateway.supports("FAKE")).isFalse();
        env.setActiveProfiles("dev", "production");
        assertThat(gateway.supports("FAKE")).isFalse();
        env.setActiveProfiles("default");
        assertThat(gateway.supports("FAKE")).isFalse();
        verifyNoInteractions(provider);
    }

    @Test
    void changingGatewayNeverSimulatesOldFakePayments() {
        MockEnvironment env = new MockEnvironment().withProperty("coffeul.payment.gateway", "portone");
        env.setActiveProfiles("dev");
        env.setProperty("coffeul.dev.payment-flow-enabled", "true");
        var provider = emptyProvider();
        var gateway = new PaymentCancellationGateway(env, provider);
        assertThat(gateway.supports("FAKE")).isFalse();
        assertThat(gateway.supports("INICIS_V2")).isFalse();
        when(provider.getIfAvailable()).thenReturn(mock(PortOnePaymentClient.class));
        assertThat(gateway.supports("INICIS_V2")).isTrue();
        assertThat(gateway.supports("FAKE")).isFalse();
        assertThat(gateway.supports("OTHER_PG")).isFalse();
        env.setProperty("coffeul.payment.gateway", "fake");
        assertThat(gateway.supports("INICIS_V2")).isFalse();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PortOnePaymentClient> emptyProvider() {
        return mock(ObjectProvider.class);
    }
}
