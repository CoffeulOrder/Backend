package com.coffeul.common.error;

import com.coffeul.common.response.ApiResponse;

import java.util.List;

/**
 * 도메인 규칙 위반 시 던지는 기본 예외. 각 모듈은 이 클래스를 상속하거나 그대로 사용한다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ApiResponse.FieldError> errors;
    private final Object data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), null, null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public BusinessException(ErrorCode errorCode, String message, List<ApiResponse.FieldError> errors) {
        this(errorCode, message, errors, null);
    }

    public static BusinessException withData(ErrorCode errorCode, String message, Object data) {
        return new BusinessException(errorCode, message, null, data);
    }

    private BusinessException(ErrorCode errorCode, String message, List<ApiResponse.FieldError> errors, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.errors = errors;
        this.data = data;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<ApiResponse.FieldError> errors() {
        return errors;
    }

    public Object data() {
        return data;
    }
}
