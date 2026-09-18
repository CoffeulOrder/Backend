package com.coffeul.member.presentation;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(
        @NotBlank(message = "이메일 인증을 다시 진행해주세요.")
        String verificationToken,

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        String newPassword
) {
}
