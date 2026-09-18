package com.coffeul.verification.presentation;

public record ConfirmVerificationCodeResponse(String verificationToken, long expiresInSeconds) {
}
