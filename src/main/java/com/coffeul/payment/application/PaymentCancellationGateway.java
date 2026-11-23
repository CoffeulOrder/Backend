package com.coffeul.payment.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.payment.infrastructure.PaymentCancellationRepository.Claim;
import com.coffeul.payment.infrastructure.PortOnePaymentClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class PaymentCancellationGateway {
    private final Environment environment;
    private final ObjectProvider<PortOnePaymentClient> portOne;

    public PaymentCancellationGateway(Environment environment, ObjectProvider<PortOnePaymentClient> portOne) {
        this.environment = environment;
        this.portOne = portOne;
    }

    public String activeProvider() {
        String mode = environment.getProperty("coffeul.payment.gateway", "fake");
        if ("portone".equals(mode) && portOne.getIfAvailable() != null) {
            return "INICIS_V2";
        }
        if ("fake".equals(mode) && environment.acceptsProfiles(Profiles.of("dev & !prod & !production"))
                && environment.getProperty("coffeul.dev.payment-flow-enabled", Boolean.class, false)) {
            return "FAKE";
        }
        return null;
    }

    public boolean supports(String provider) {
        return provider != null && provider.equals(activeProvider());
    }

    public boolean cancel(Claim claim) {
        var payment = claim.payment();
        if (!supports(payment.provider())) {
            throw new BusinessException(PaymentErrorCode.SETUP_NOT_READY);
        }
        if (payment.amount() != claim.amount() || claim.amount() <= 0) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
        }
        if ("FAKE".equals(payment.provider())) {
            return true;
        }
        return portOne.getObject().cancelFullPayment(new PortOnePaymentClient.CancellationPayment(
                payment.paymentId(), payment.transactionId(), payment.storeId(), payment.channelKey(),
                payment.amount()), claim.idempotencyKey());
    }
}
