package com.coffeul.member.infrastructure;

import com.coffeul.verification.api.MemberAccountPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * verification이 SM-1에서 "이미 가입된 이메일인지"를 확인할 때 쓰는 어댑터 (REQ-EV-006).
 * <p>탈퇴 회원은 email이 NULL이라 findByEmail에 잡히지 않는다 — 같은 학교 메일로 다시 가입할 수 있다.
 */
@Component
public class MemberAccountPortImpl implements MemberAccountPort {

    private final MemberRepository memberRepository;

    public MemberAccountPortImpl(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return memberRepository.findByEmail(email).isPresent();
    }
}
