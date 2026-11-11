package com.coffeul.menu.presentation;

import java.util.List;

/** MS-11 메뉴 상세 응답. */
public record MenuDetailResponse(Long menuId, String name, String description, int basePrice, String imageUrl,
                                 boolean soldOut, List<OptionGroup> optionGroups) {

    public record OptionGroup(Long optionGroupId, String name, String type, boolean required, int minSelect,
                              int maxSelect, List<Option> options) {
    }

    public record Option(Long optionItemId, String name, int priceDelta, boolean isDefault, boolean soldOut) {
    }
}
