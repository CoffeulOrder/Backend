package com.coffeul.order.domain;

import com.coffeul.menu.api.MenuItemView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MenuSelectionValidatorTest {

    // MS-11 예시와 같은 값: 아메리카노 R 1,500 / L +500, 온도 HOT·ICE(둘 다 +0)
    private static final MenuItemView.OptionItemView HOT = new MenuItemView.OptionItemView(1L, 1L, "HOT", 0, false);
    private static final MenuItemView.OptionItemView ICE = new MenuItemView.OptionItemView(2L, 1L, "ICE", 0, false);
    private static final MenuItemView.OptionItemView SIZE_R = new MenuItemView.OptionItemView(3L, 2L, "R", 0, false);
    private static final MenuItemView.OptionItemView SIZE_L = new MenuItemView.OptionItemView(4L, 2L, "L", 500, false);
    private static final MenuItemView.OptionItemView SIZE_L_SOLD_OUT = new MenuItemView.OptionItemView(4L, 2L, "L", 500, true);

    private static final MenuItemView.OptionGroupView TEMP_GROUP =
            new MenuItemView.OptionGroupView(1L, "온도", true, 1, 1, List.of(HOT, ICE));

    private static MenuItemView americano(MenuItemView.OptionItemView sizeL) {
        MenuItemView.OptionGroupView sizeGroup =
                new MenuItemView.OptionGroupView(2L, "사이즈", true, 1, 1, List.of(SIZE_R, sizeL));
        return new MenuItemView(1L, 1L, "아메리카노", 1500, false, List.of(TEMP_GROUP, sizeGroup));
    }

    @Test
    void validSelectionSumsDeltasAndEchoesNames() {
        var result = MenuSelectionValidator.validate(americano(SIZE_L), List.of(2L, 4L)); // ICE · L

        assertEquals(500, result.optionPriceDeltaSum());
        assertEquals(2, result.selectedOptions().size());
        assertEquals("ICE", result.selectedOptions().get(0).itemName());
        assertEquals("L", result.selectedOptions().get(1).itemName());
    }

    @Test
    void missingRequiredGroupThrows() {
        // 사이즈를 아예 안 고름 — required=true, minSelect=1인데 0개
        assertThrows(InvalidOptionSelectionException.class,
                () -> MenuSelectionValidator.validate(americano(SIZE_L), List.of(1L)));
    }

    @Test
    void soldOutOptionThrows() {
        assertThrows(InvalidOptionSelectionException.class,
                () -> MenuSelectionValidator.validate(americano(SIZE_L_SOLD_OUT), List.of(1L, 4L)));
    }

    @Test
    void unknownOptionItemIdThrows() {
        // 이 메뉴 소속이 아닌 optionItemId(999)가 섞여 들어옴
        assertThrows(InvalidOptionSelectionException.class,
                () -> MenuSelectionValidator.validate(americano(SIZE_L), List.of(1L, 3L, 999L)));
    }

    @Test
    void noOptionGroupsMenuAllowsEmptySelection() {
        MenuItemView noOptions = new MenuItemView(9L, 1L, "에스프레소", 2000, false, List.of());
        var result = MenuSelectionValidator.validate(noOptions, List.of());
        assertEquals(0, result.optionPriceDeltaSum());
        assertEquals(0, result.selectedOptions().size());
    }
}
