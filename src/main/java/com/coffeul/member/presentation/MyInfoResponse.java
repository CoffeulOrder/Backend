package com.coffeul.member.presentation;

import java.time.OffsetDateTime;

/** SM-4 응답 (REQ-U-004). */
public record MyInfoResponse(
        Long memberId,
        String name,
        String email,
        SchoolSummary school,
        OffsetDateTime createdAt
) {

    public record SchoolSummary(Long schoolId, String name, String campus) {
    }
}
