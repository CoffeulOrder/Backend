package com.coffeul.order.domain;

/** 주문 항목 금액 계산 — DB · Spring 없이 단독으로 도는 순수 함수 (rules.py TEST_STRATEGY: 단위 테스트). */
public final class LinePricing {

    private LinePricing() {
    }

    public static int unitPrice(int basePrice, int optionPriceDeltaSum) {
        return basePrice + optionPriceDeltaSum;
    }

    public static int lineAmount(int unitPrice, int quantity) {
        return unitPrice * quantity;
    }

    public static int totalOf(int subtotalAmount, int discountAmount) {
        return subtotalAmount - discountAmount;
    }
}
