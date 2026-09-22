package com.coffeul.verification.presentation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendVerificationCodeRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않아요.")
        @Size(max = 100, message = "이메일 형식이 올바르지 않아요.")
        String email,

        @NotBlank(message = "인증 용도가 필요해요.")
        String purpose
) {
}
