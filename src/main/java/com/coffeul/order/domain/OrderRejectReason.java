package com.coffeul.order.domain;

/** DB에 정의된 매장 거절 사유. */
public enum OrderRejectReason {
    SOLD_OUT, INGREDIENT_SHORTAGE, STORE_BUSY, STORE_CLOSING, OTHER
}
