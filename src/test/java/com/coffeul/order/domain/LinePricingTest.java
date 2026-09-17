package com.coffeul.order.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinePricingTest {

    @Test
    void unitPriceAddsOptionDeltas() {
        // 아메리카노 ICE·L: 기본가 1500 + 사이즈 500 = 2000 (설계안 공식 CSV 값)
        assertEquals(2000, LinePricing.unitPrice(1500, 500));
    }

    @Test
    void lineAmountMultipliesByQuantity() {
        assertEquals(4000, LinePricing.lineAmount(2000, 2));
    }

    @Test
    void totalIsSubtotalMinusDiscount() {
        assertEquals(6500, LinePricing.totalOf(6500, 0));
        assertEquals(5000, LinePricing.totalOf(6500, 1500));
    }
}
