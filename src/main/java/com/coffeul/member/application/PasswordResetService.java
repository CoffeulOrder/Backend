package com.coffeul.member.application;

import com.coffeul.auth.api.RefreshTokenRevoker;
import com.coffeul.common.error.BusinessException;
import com.coffeul.member.domain.Member;
import com.coffeul.member.domain.PasswordPolicy;
import com.coffeul.member.infrastructure.MemberRepository;
import com.coffeul.verification.api.EmailVerificationApi;
import com.coffeul.verification.api.VerificationPurpose;
import com.coffeul.verification.api.VerificationErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SM-6(비밀번호 재설정) — REQ-U-006. */
@Service
public class PasswordResetService {

    private final EmailVerificationApi emailVerificationApi;
    private final MemberRepository memberRepository;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(EmailVerificationApi emailVerificationApi,
                                 MemberRepository memberRepository,
                                 RefreshTokenRevoker refreshTokenRevoker,
                                 PasswordEncoder passwordEncoder) {
        this.emailVerificationApi = emailVerificationApi;
        this.memberRepository = memberRepository;
        this.refreshTokenRevoker = refreshTokenRevoker;
        this.passwordEncoder = passwordEncoder;
    }

    /** 비밀번호 변경 · 토큰 소비 · refresh 폐기는 한 트랜잭션. 하나라도 실패하면 인증 토큰도 쓰이지 않은 채로 남는다. */
    @Transactional
    public void reset(String verificationToken, String newPassword) {
        // 규칙 위반을 먼저 걸러서, 오타 하나로 인증 토큰이 타버리지 않게 한다.
        if (!PasswordPolicy.isValid(newPassword)) {
            throw new BusinessException(MemberErrorCode.PASSWORD_POLICY_VIOLATION);
        }

        EmailVerificationApi.VerifiedEmail verified =
                emailVerificationApi.consume(verificationToken, VerificationPurpose.PASSWORD_RESET);

        // 가입되지 않은 이메일에도 코드 행은 만들되 메일은 보내지 않는다(SM-1). 그래서 여기 도달하려면
        // 코드를 찍어 맞힌 경우뿐이고, 가입 여부를 드러내지 않도록 토큰 문제와 같은 응답을 준다.
        Member member = memberRepository.findByEmail(verified.email())
                .orElseThrow(() -> new BusinessException(VerificationErrorCode.VERIFICATION_TOKEN_INVALID));

        member.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenRevoker.revokeAllForMember(member.getId());
    }
}
