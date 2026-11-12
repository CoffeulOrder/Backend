package com.coffeul.payment.api;

public record PaymentAttemptView(int attemptNo, String pgProvider, String pgOrderId, String clientKey,
                                 PortOneCheckout portOne) {
    public PaymentAttemptView(int attemptNo, String pgProvider, String pgOrderId, String clientKey) {
        this(attemptNo, pgProvider, pgOrderId, clientKey, null);
    }

    public record PortOneCheckout(String storeId, String channelKey, String paymentId, String currency) { }
}
