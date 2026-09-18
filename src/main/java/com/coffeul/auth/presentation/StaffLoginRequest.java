package com.coffeul.auth.presentation;

import jakarta.validation.constraints.NotBlank;

/** MS-2 요청 본문. */
public record StaffLoginRequest(
        @NotBlank(message = "아이디를 입력해주세요.") String loginId,
        @NotBlank(message = "비밀번호를 입력해주세요.") String password
) {
}
