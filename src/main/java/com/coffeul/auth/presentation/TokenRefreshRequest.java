package com.coffeul.auth.presentation;

import jakarta.validation.constraints.NotBlank;

/** MS-3 요청 본문. */
public record TokenRefreshRequest(@NotBlank String refreshToken) {
}
