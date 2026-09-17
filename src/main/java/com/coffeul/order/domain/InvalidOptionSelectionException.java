package com.coffeul.order.domain;

/** 옵션 선택이 메뉴의 옵션 그룹 규칙(필수 · min/max · 품절)과 안 맞을 때. 순수 도메인 예외 — HTTP 매핑은 application 계층이 한다. */
public class InvalidOptionSelectionException extends RuntimeException {

    private final Long menuItemId;
    private final String optionGroupName;

    public InvalidOptionSelectionException(Long menuItemId, String optionGroupName) {
        super("메뉴 " + menuItemId + "의 옵션 선택이 올바르지 않음"
                + (optionGroupName != null ? " (그룹: " + optionGroupName + ")" : ""));
        this.menuItemId = menuItemId;
        this.optionGroupName = optionGroupName;
    }

    public Long menuItemId() {
        return menuItemId;
    }

    public String optionGroupName() {
        return optionGroupName;
    }
}
