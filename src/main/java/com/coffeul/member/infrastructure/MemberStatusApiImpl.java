package com.coffeul.member.infrastructure;

import com.coffeul.member.api.MemberStatusApi;
import com.coffeul.member.domain.Member;
import org.springframework.stereotype.Component;

@Component
public class MemberStatusApiImpl implements MemberStatusApi {

    private final MemberRepository memberRepository;

    public MemberStatusApiImpl(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public boolean isActive(Long memberId) {
        return memberRepository.findById(memberId).map(Member::isActive).orElse(false);
    }
}
