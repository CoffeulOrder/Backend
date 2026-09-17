package com.coffeul.order.presentation;

import java.util.List;

/** MS-16 요청 본문. */
public record OrderCreateRequest(
        Long storeId,
        List<Item> items,
        String requestMemo,
        Boolean withdrawalLimitAgreed
) {
    public record Item(Long menuId, Integer quantity, List<Long> optionItemIds) {
    }
}
