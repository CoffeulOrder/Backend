package com.coffeul.auth.presentation;

import jakarta.validation.constraints.NotBlank;

/** MS-1 요청 본문. */
public record MemberLoginRequest(
        @NotBlank(message = "이메일을 입력해주세요.") String email,
        @NotBlank(message = "비밀번호를 입력해주세요.") String password
) {
}
