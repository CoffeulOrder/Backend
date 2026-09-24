package com.coffeul.order.presentation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderHistoryResponse(LocalDate businessDate, Summary summary, List<Item> orders) {

    public record Summary(long completed, long canceled, long rejected, long inProgress) {
    }

    public record Item(Long orderId, Short pickupNo, String status, int totalAmount, String itemSummary,
                        OffsetDateTime placedAt, OffsetDateTime completedAt) {
    }
}
