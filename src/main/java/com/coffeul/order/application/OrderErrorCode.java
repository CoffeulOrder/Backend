package com.coffeul.order.application;

import com.coffeul.common.error.ErrorCode;

/** 주문 생성(MS-16) 실패 코드 — api.py 원본 그대로. */
public enum OrderErrorCode implements ErrorCode {

    MENU_NOT_FOUND(404, "MN001", "메뉴를 찾을 수 없어요."),
    INVALID_OPTION_SELECTION(400, "OD003", "옵션을 다시 선택해주세요."),
    QUANTITY_LIMIT_EXCEEDED(400, "OD004", "한 번에 주문할 수 있는 수량을 넘었어요."),
    WITHDRAWAL_LIMIT_NOT_AGREED(400, "OD007", "주문 취소 제한 안내에 동의해주세요."),
    SOLD_OUT(409, "OD002", "품절된 메뉴가 있어요."),
    IDEMPOTENCY_CONFLICT(409, "OD010", "같은 요청 키로 다른 주문을 보낼 수 없어요."),
    MEMBER_NOT_USABLE(403, "AUTH_005", "이용할 수 없는 계정이에요.");

    private final int status;
    private final String code;
    private final String message;

    OrderErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
