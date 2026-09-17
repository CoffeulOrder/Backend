package com.coffeul.member.api;

/** 다른 모듈이 회원이 있고 이용 가능한 상태인지만 확인할 때 쓰는 포트. */
public interface MemberStatusApi {

    boolean isActive(Long memberId);
}
