package com.coffeul.payment.api;

public record PaymentAttemptView(int attemptNo, String pgProvider, String pgOrderId, String clientKey) {
}
