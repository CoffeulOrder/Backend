package com.coffeul.common.error;

import com.coffeul.common.response.ApiResponse;

import java.util.List;

/**
 * 도메인 규칙 위반 시 던지는 기본 예외. 각 모듈은 이 클래스를 상속하거나 그대로 사용한다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ApiResponse.FieldError> errors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, List<ApiResponse.FieldError> errors) {
        super(message);
        this.errorCode = errorCode;
        this.errors = errors;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<ApiResponse.FieldError> errors() {
        return errors;
    }
}
