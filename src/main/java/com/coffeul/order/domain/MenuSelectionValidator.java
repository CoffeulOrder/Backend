package com.coffeul.order.domain;

import com.coffeul.menu.api.MenuItemView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 메뉴의 옵션 그룹 규칙(필수 · min/max · 품절)에 맞춰 고객이 고른 옵션을 검증하고 추가금 합계를 낸다.
 * DB · Spring 없이 도는 순수 함수라 단독으로 단위 테스트한다 (rules.py TEST_STRATEGY).
 */
public final class MenuSelectionValidator {

    private MenuSelectionValidator() {
    }

    public record SelectedOption(Long optionItemId, String groupName, String itemName, int priceDelta) {
    }

    public record Result(int optionPriceDeltaSum, List<SelectedOption> selectedOptions) {
    }

    public static Result validate(MenuItemView menuItem, List<Long> requestedOptionItemIds) {
        Set<Long> remaining = new LinkedHashSet<>(requestedOptionItemIds);
        List<SelectedOption> selected = new ArrayList<>();
        int totalDelta = 0;

        for (MenuItemView.OptionGroupView group : menuItem.optionGroups()) {
            List<MenuItemView.OptionItemView> picked = group.options().stream()
                    .filter(o -> remaining.contains(o.id()))
                    .toList();

            if (picked.size() < group.minSelect() || picked.size() > group.maxSelect()) {
                throw new InvalidOptionSelectionException(menuItem.id(), group.name());
            }
            for (MenuItemView.OptionItemView option : picked) {
                if (option.soldOut()) {
                    throw new InvalidOptionSelectionException(menuItem.id(), group.name());
                }
                selected.add(new SelectedOption(option.id(), group.name(), option.name(), option.priceDelta()));
                totalDelta += option.priceDelta();
                remaining.remove(option.id());
            }
        }

        if (!remaining.isEmpty()) {
            // 이 메뉴 소속이 아닌 optionItemId가 섞여 들어옴
            throw new InvalidOptionSelectionException(menuItem.id(), null);
        }
        return new Result(totalDelta, selected);
    }
}
