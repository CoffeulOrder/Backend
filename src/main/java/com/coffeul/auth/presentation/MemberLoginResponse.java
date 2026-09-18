package com.coffeul.auth.presentation;

/** MS-1 응답. */
public record MemberLoginResponse(String accessToken, String refreshToken, long expiresIn, MemberSummary member) {

    public record MemberSummary(Long memberId, String name, Long schoolId) {
    }
}
