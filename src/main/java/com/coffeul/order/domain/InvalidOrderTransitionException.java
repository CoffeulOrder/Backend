package com.coffeul.order.domain;

/** 주문이 기대한 상태가 아닐 때(MS-22~26·만료 작업 공통). 순수 도메인 예외 — HTTP 매핑(OD006)은 application 계층이 한다. */
public class InvalidOrderTransitionException extends RuntimeException {

    private final String currentStatus;

    public InvalidOrderTransitionException(String currentStatus) {
        super("지금 상태(" + currentStatus + ")에서는 할 수 없는 요청");
        this.currentStatus = currentStatus;
    }

    public String currentStatus() {
        return currentStatus;
    }
}
