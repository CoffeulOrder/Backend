package com.coffeul.store.api;

import com.coffeul.common.error.ErrorCode;

public enum StoreErrorCode implements ErrorCode {

    NOT_FOUND(404, "ST001", "매장을 찾을 수 없어요."),
    NOT_ACCEPTING_ORDERS(409, "OD001", "지금은 주문을 받지 않아요."),
    FORBIDDEN(403, "AUTH_004", "이 매장에 접근할 권한이 없어요."),
    ACTIVE_ORDERS_REMAIN(409, "ST002", "처리 중인 주문이 있어요. 그래도 마감할까요?"),
    INVALID_STATUS(400, "ST003", "영업 상태 값이 올바르지 않아요.");

    private final int status;
    private final String code;
    private final String message;

    StoreErrorCode(int status, String code, String message) {
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
