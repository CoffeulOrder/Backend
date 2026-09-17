package com.coffeul.menu.presentation;

/** MS-10 응답 항목. */
public record MenuResponse(
        Long menuId,
        String name,
        String description,
        int basePrice,
        String imageUrl,
        boolean soldOut,
        boolean isNew,
        boolean isEvent,
        boolean hasOptions
) {
}
