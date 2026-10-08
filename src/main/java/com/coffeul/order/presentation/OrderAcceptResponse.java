package com.coffeul.order.presentation;

import java.time.OffsetDateTime;

public record OrderAcceptResponse(Long orderId, String status, OffsetDateTime acceptedAt) {
}
