package com.coffeul.order.presentation;

import com.coffeul.payment.api.PaymentAttemptView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/** MS-16 응답. */
public record OrderCreateResponse(
        Long orderId,
        String orderCode,
        String status,
        OffsetDateTime expiresAt,
        List<LineItem> items,
        int subtotalAmount,
        int discountAmount,
        int totalAmount,
        PaymentInfo payment
) {
    public record LineItem(String menuName, List<String> options, int unitPrice, int quantity, int lineAmount) {
    }

    public record PaymentInfo(int attemptNo, String pgProvider, String pgOrderId, String orderName,
                               int amount, String clientKey,
                               @JsonInclude(JsonInclude.Include.NON_NULL) PaymentAttemptView.PortOneCheckout portOne) {
    }
}
