package com.coffeul.verification.presentation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmVerificationCodeRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않아요.")
        String email,

        @NotBlank(message = "인증 용도가 필요해요.")
        String purpose,

        @Pattern(regexp = "\\d{6}", message = "인증코드는 숫자 6자리예요.")
        String code
) {
}
