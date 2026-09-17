package com.coffeul.menu.api;

import java.util.List;

/** order 등 다른 모듈이 주문 생성 시점에 필요한 메뉴 정보만 담는 읽기 전용 뷰. */
public record MenuItemView(
        Long id,
        Long storeId,
        String name,
        int basePrice,
        boolean soldOut,
        List<OptionGroupView> optionGroups
) {

    public record OptionGroupView(
            Long id,
            String name,
            boolean required,
            int minSelect,
            int maxSelect,
            List<OptionItemView> options
    ) {
    }

    public record OptionItemView(
            Long id,
            Long optionGroupId,
            String name,
            int priceDelta,
            boolean soldOut
    ) {
    }
}
