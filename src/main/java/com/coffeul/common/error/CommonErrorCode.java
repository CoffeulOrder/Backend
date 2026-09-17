package com.coffeul.common.error;

/**
 * 모든 모듈이 공유하는 공통 에러 코드 (api.py 원본 표 그대로).
 * 도메인 전용 코드(AUTH_xxx, SE001 등)는 각 모듈에서 ErrorCode를 구현해 따로 정의한다.
 */
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_FAILED(400, "C001", "입력값 검증 실패"),
    UNAUTHORIZED(401, "C002", "다시 로그인해주세요."),
    FORBIDDEN(403, "C003", "권한이 없어요."),
    NOT_FOUND(404, "C004", "요청한 정보를 찾을 수 없어요."),
    TOO_MANY_REQUESTS(429, "C005", "요청이 너무 많아요. 잠시 후 다시 시도해주세요."),
    INTERNAL_ERROR(500, "C006", "일시적인 오류예요. 잠시 후 다시 시도해주세요."),
    CONFLICT(409, "C007", "다른 사람이 먼저 수정했어요. 다시 불러와 주세요.");

    private final int status;
    private final String code;
    private final String message;

    CommonErrorCode(int status, String code, String message) {
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
