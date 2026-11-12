package com.coffeul.payment.api;

import com.coffeul.common.error.ErrorCode;

public enum PaymentErrorCode implements ErrorCode {
    AMOUNT_MISMATCH(400, "PY001", "결제 금액이 주문 금액과 달라요."),
    ATTEMPT_NOT_FOUND(404, "PY005", "결제 정보를 찾을 수 없어요."),
    SETUP_NOT_READY(503, "PY006", "매장 결제 설정이 준비되지 않았어요."),
    INVALID_STATE(409, "PY008", "지금 결제를 확정할 수 없어요."),
    NOT_PAID(409, "PY009", "결제 완료가 아직 확인되지 않았어요."),
    LOOKUP_UNAVAILABLE(503, "PY010", "결제 상태를 확인할 수 없어요. 잠시 후 다시 확인해주세요."),
    PAYMENT_CONTEXT_MISMATCH(409, "PY011", "주문의 결제 정보가 일치하지 않아요."),
    ORDER_EXPIRED(409, "OD009", "결제 시간이 지나 주문이 만료됐어요.");

    private final int status;
    private final String code;
    private final String message;

    PaymentErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
    public int status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
