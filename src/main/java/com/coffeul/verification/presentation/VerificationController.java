package com.coffeul.verification.presentation;

import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.verification.application.ConfirmVerificationCodeService;
import com.coffeul.verification.application.SendVerificationCodeService;
import com.coffeul.verification.domain.VerificationPurpose;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** SM-1 · SM-2 (학교 이메일 인증코드 발송 · 확인). */
@RestController
@RequestMapping("/api/v1/verifications")
public class VerificationController {

    private final SendVerificationCodeService sendVerificationCodeService;
    private final ConfirmVerificationCodeService confirmVerificationCodeService;

    public VerificationController(SendVerificationCodeService sendVerificationCodeService,
                                   ConfirmVerificationCodeService confirmVerificationCodeService) {
        this.sendVerificationCodeService = sendVerificationCodeService;
        this.confirmVerificationCodeService = confirmVerificationCodeService;
    }

    @PostMapping("/email")
    public ResponseEntity<ApiResponse<SendVerificationCodeResponse>> send(
            @Valid @RequestBody SendVerificationCodeRequest request) {
        SendVerificationCodeService.Result result =
                sendVerificationCodeService.send(request.email(), purposeOf(request.purpose()));
        SendVerificationCodeResponse body = new SendVerificationCodeResponse(
                result.email(), result.expiresInSeconds(), result.resendAvailableInSeconds());
        long minutes = TimeUnit.SECONDS.toMinutes(result.expiresInSeconds());
        return ResponseEntity.ok(ApiResponse.success(
                "인증코드를 보냈어요. %d분 안에 입력해주세요.".formatted(minutes), body));
    }

    @PostMapping("/email/confirm")
    public ResponseEntity<ApiResponse<ConfirmVerificationCodeResponse>> confirm(
            @Valid @RequestBody ConfirmVerificationCodeRequest request) {
        ConfirmVerificationCodeService.Result result = confirmVerificationCodeService
                .confirm(request.email(), purposeOf(request.purpose()), request.code());
        ConfirmVerificationCodeResponse body =
                new ConfirmVerificationCodeResponse(result.verificationToken(), result.expiresInSeconds());
        return ResponseEntity.ok(ApiResponse.success("이메일 인증이 완료됐어요.", body));
    }

    // purpose를 enum 파라미터로 받으면 Jackson이 먼저 터져서 공통 검증 응답(C001) 대신 500이 나간다.
    private VerificationPurpose purposeOf(String raw) {
        return VerificationPurpose.parse(raw)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.VALIDATION_FAILED,
                        CommonErrorCode.VALIDATION_FAILED.message(),
                        List.of(new ApiResponse.FieldError("purpose", "SIGNUP 또는 PASSWORD_RESET이어야 해요."))));
    }
}
