package com.coffeul.member.presentation;

public record MemberRegisterResponse(
        Long memberId,
        String name,
        String email,
        SchoolSummary school,
        String accessToken,
        String refreshToken,
        long expiresIn
) {

    public record SchoolSummary(Long schoolId, String name, String campus) {
    }
}
