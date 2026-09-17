package com.coffeul.order.presentation;

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

    /** PG사 확정 전까지는 가짜 어댑터 값 — rules.py PAYMENT_RULES: "PG사 확정 전에는 가짜 어댑터로 전 흐름을 완성한다." */
    public record PaymentInfo(int attemptNo, String pgProvider, String pgOrderId, String orderName,
                               int amount, String clientKey) {
    }
}
