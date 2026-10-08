package com.coffeul.order.presentation;

import java.time.OffsetDateTime;
import java.util.List;

public record OrderBoardResponse(OffsetDateTime serverTime, List<Item> orders) {

    public record Item(Long orderId, String orderCode, Short pickupNo, String status, OffsetDateTime placedAt,
                        long elapsedSeconds, List<LineItem> items, String requestMemo) {
    }

    public record LineItem(String menuName, List<String> options, int quantity) {
    }
}
