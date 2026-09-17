package com.coffeul.store.api;

import java.math.BigDecimal;

public record StoreView(Long id, String name, String status, Long schoolId, BigDecimal commissionRate) {

    public boolean isOpen() {
        return "OPEN".equals(status);
    }
}
