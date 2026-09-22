package com.coffeul.member.application;

import com.coffeul.auth.api.RefreshTokenRevoker;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.member.domain.Member;
import com.coffeul.member.domain.PasswordPolicy;
import com.coffeul.member.infrastructure.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SM-5(비밀번호 변경) — REQ-U-005. */
@Service
public class PasswordChangeService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final PasswordEncoder passwordEncoder;

    public PasswordChangeService(MemberRepository memberRepository,
                                  RefreshTokenRevoker refreshTokenRevoker,
                                  PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.refreshTokenRevoker = refreshTokenRevoker;
        this.passwordEncoder = passwordEncoder;
    }

    /** 비밀번호 변경과 refresh 폐기는 한 트랜잭션 — 바꿨는데 기존 세션이 살아 있는 중간 상태를 만들지 않는다. */
    @Transactional
    public void change(Long memberId, String currentPassword, String newPassword) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));

        // 탈퇴 회원은 password_hash가 NULL이라 아래 matches에서 어차피 걸리지만, 그 경우 U004("현재 비밀번호가
        // 올바르지 않아요")는 오해를 부른다. 계정 자체가 못 쓰는 상태라는 뜻의 C002로 먼저 막는다.
        if (!member.isActive()) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
            throw new BusinessException(MemberErrorCode.PASSWORD_MISMATCH);
        }
        if (!PasswordPolicy.isValid(newPassword)) {
            throw new BusinessException(MemberErrorCode.PASSWORD_POLICY_VIOLATION);
        }

        member.changePassword(passwordEncoder.encode(newPassword));

        // 비밀번호를 바꾸는 이유가 "남이 내 계정을 쓰고 있다"인 경우가 많다. 기존 세션을 남기면 침입자가
        // 그대로 로그인 상태로 남으므로 전부 폐기한다 — 그래서 성공 메시지도 "다시 로그인해주세요"다.
        refreshTokenRevoker.revokeAllForMember(member.getId());
    }
}
