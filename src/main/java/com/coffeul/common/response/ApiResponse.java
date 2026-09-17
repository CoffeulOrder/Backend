package com.coffeul.common.response;

import com.coffeul.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 모든 API 응답의 공통 봉투. 필드·형식은 REST API 명세(api.py)의
 * {@code {timestamp, status, code, message, data, errors}} 원본을 그대로 따른다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        T data,
        List<FieldError> errors
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(OffsetDateTime.now(), 200, "SUCCESS", message, data, null);
    }

    public static <T> ApiResponse<T> success(String message) {
        return success(message, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, List<FieldError> errors) {
        return new ApiResponse<>(OffsetDateTime.now(), errorCode.status(), errorCode.code(), message, null, errors);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.message(), null);
    }

    public record FieldError(String field, String reason) {
    }
}
