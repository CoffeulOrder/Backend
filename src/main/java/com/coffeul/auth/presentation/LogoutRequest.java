package com.coffeul.auth.presentation;

/** MS-4 요청 본문. pushToken은 선택 — 있으면 notification.api로 비활성 요청(모듈 미구현이라 지금은 무시). */
public record LogoutRequest(String refreshToken, String pushToken) {
}
