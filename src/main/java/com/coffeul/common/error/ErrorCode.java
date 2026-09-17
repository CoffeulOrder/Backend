package com.coffeul.common.error;

/**
 * 도메인별 에러 코드가 구현하는 인터페이스.
 * 코드 체계(CODE 접두어)는 REST API 명세(api.py) 원본을 따른다 — 여기서 새로 정하지 않는다.
 */
public interface ErrorCode {

    int status();

    String code();

    String message();
}
