package com.coffeul.member.application;

/**
 * 탈퇴한 회원의 푸시 토큰을 전부 비활성화한다 (REQ-U-007: "refresh 토큰 폐기 · 푸시 토큰 비활성까지 한 번에").
 *
 * <p><b>임시</b>: `device_token`의 주인 모듈(notification, 태완 형 담당)이 아직 없다. 생기면 이 포트의
 * 어댑터만 notification.api 호출로 바꾸면 되고, 서비스는 그대로 둔다 — SchoolLookupPort가 store.api로
 * 옮겨간 것과 같은 방식이다.
 */
public interface PushTokenDeactivationPort {

    /** @return 비활성으로 바꾼 토큰 수 (없으면 0 — 멱등) */
    int deactivateAllForMember(Long memberId);
}
