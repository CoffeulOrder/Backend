package com.coffeul.verification.presentation;

public record SendVerificationCodeResponse(String email, long expiresInSeconds, long resendAvailableInSeconds) {
}
