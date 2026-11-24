package com.coffeul.menu.presentation;

import java.util.List;

/** MS-12: 숨김 메뉴를 포함한 매장 품절 관리 목록. */
public record StaffMenuResponse(Long menuId, String name, String status, boolean soldOut,
                                List<OptionGroup> optionGroups) {
    public record OptionGroup(Long optionGroupId, String name, List<Option> options) { }
    public record Option(Long optionItemId, String name, boolean soldOut) { }
}
