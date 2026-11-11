package com.coffeul.order;

import com.coffeul.order.application.DevOrderPaymentService;
import com.coffeul.order.presentation.DevOrderPaymentController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DevPaymentActivationTest {
    @ParameterizedTest
    @CsvSource({"dev,true,true", "dev,false,false", "prod,true,false", "dev+prod,true,false",
            "dev+production,true,false", "default,true,false"})
    void simulationRouteRequiresExplicitDevelopmentOptIn(String profiles, boolean enabled, boolean expected) {
        new ApplicationContextRunner()
                .withInitializer(context -> {
                    context.getEnvironment().setActiveProfiles(profiles.split("\\+"));
                    context.getBeanFactory().registerSingleton("testPaymentService", mock(DevOrderPaymentService.class));
                })
                .withPropertyValues("coffeul.dev.payment-flow-enabled=" + enabled)
                .withUserConfiguration(DevOrderPaymentController.class)
                .run(context -> assertThat(context.getBeansOfType(DevOrderPaymentController.class).isEmpty()).isEqualTo(!expected));
    }
}
