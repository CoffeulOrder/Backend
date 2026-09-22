package com.coffeul.order.presentation;

import java.time.OffsetDateTime;

public record OrderReadyResponse(Long orderId, String status, OffsetDateTime readyAt) {
}
