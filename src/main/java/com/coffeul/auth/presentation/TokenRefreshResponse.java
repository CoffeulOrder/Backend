package com.coffeul.auth.presentation;

/** MS-3 응답. */
public record TokenRefreshResponse(String accessToken, String refreshToken, long expiresIn) {
}
