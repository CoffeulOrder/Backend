package com.coffeul.order.presentation;

import java.time.OffsetDateTime;

public record OrderCompleteResponse(Long orderId, String status, OffsetDateTime completedAt) {
}
