package com.coffeul.store.presentation;

import java.time.OffsetDateTime;

public record StoreStatusChangeResponse(Long storeId, String status, OffsetDateTime changedAt) {
}
