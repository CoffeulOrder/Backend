package com.coffeul.order.presentation;

import java.time.OffsetDateTime;
import java.util.List;

/** MS-18 응답 data. aheadCount · rejectReason은 해당 없을 때 null이다. */
public record OrderDetailResponse(Long orderId, String orderCode, String status, Store store, Short pickupNo,
                                   Long aheadCount, boolean cancelable, List<Line> items,
                                   int subtotalAmount, int discountAmount, int totalAmount,
                                   String requestMemo, String rejectReason, Timeline timeline) {

    public record Store(Long storeId, String name) {
    }

    public record Line(String menuName, List<String> options, int unitPrice, int quantity, int lineAmount) {
    }

    public record Timeline(OffsetDateTime placedAt, OffsetDateTime acceptedAt, OffsetDateTime makingAt,
                            OffsetDateTime readyAt, OffsetDateTime completedAt, OffsetDateTime canceledAt,
                            OffsetDateTime rejectedAt) {
    }
}
