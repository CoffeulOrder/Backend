package com.coffeul.order.presentation;

import java.time.OffsetDateTime;

public record OrderStartResponse(Long orderId, String status, OffsetDateTime makingAt) {
}
