package com.coffeul.order.presentation;

import java.time.OffsetDateTime;
import java.util.List;

/** MS-17 응답 data. */
public record OrderListResponse(List<Item> content, int page, int size, boolean hasNext) {

    public record Item(Long orderId, String orderCode, String storeName, String status, Short pickupNo,
                        String itemSummary, int totalAmount, OffsetDateTime placedAt) {
    }
}
