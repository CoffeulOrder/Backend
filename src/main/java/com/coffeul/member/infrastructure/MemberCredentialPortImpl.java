package com.coffeul.member.infrastructure;

import com.coffeul.auth.api.MemberCredentialPort;
import com.coffeul.member.domain.Member;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** auth가 MS-1(고객 로그인)에서 회원 자격을 조회할 때 쓰는 어댑터 (rules.py AUTH_SPEC: 역방향 포트). */
@Component
public class MemberCredentialPortImpl implements MemberCredentialPort {

    private final MemberRepository memberRepository;

    public MemberCredentialPortImpl(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MemberCredential> findByEmail(String email) {
        return memberRepository.findByEmail(email).map(this::toCredential);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MemberCredential> findById(Long memberId) {
        return memberRepository.findById(memberId).map(this::toCredential);
    }

    @Override
    @Transactional
    public void recordLoginSuccess(Long memberId, Instant now) {
        memberRepository.findById(memberId).ifPresent(member -> member.recordLoginSuccess(now));
    }

    @Override
    @Transactional
    public void recordLoginFailure(Long memberId, Instant now, Instant lockedUntil) {
        memberRepository.findById(memberId).ifPresent(member -> member.recordLoginFailure(lockedUntil));
    }

    private MemberCredential toCredential(Member member) {
        return new MemberCredential(member.getId(), member.getSchoolId(), member.getName(),
                member.getPasswordHash(), member.isActive(), member.getFailedLoginCount(), member.getLockedUntil());
    }
}
