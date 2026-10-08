package com.coffeul.member.application;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.api.TokenIssuer;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.member.domain.Member;
import com.coffeul.member.domain.PasswordPolicy;
import com.coffeul.member.domain.TermsAgreement;
import com.coffeul.member.domain.TermsType;
import com.coffeul.member.infrastructure.MemberRepository;
import com.coffeul.member.infrastructure.TermsAgreementRepository;
import com.coffeul.verification.api.EmailVerificationApi;
import com.coffeul.verification.api.VerificationPurpose;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** SM-3(회원가입) — REQ-U-001 · 002 · 003, REQ-EV-005. */
@Service
public class MemberRegisterService {

    /** 둘 다 필수 동의 (REQ-U-003). 선택 약관이 생기면 여기서 나눈다. */
    private static final Set<TermsType> REQUIRED_TERMS = EnumSet.allOf(TermsType.class);

    private final EmailVerificationApi emailVerificationApi;
    private final MemberRepository memberRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final SchoolLookupPort schoolLookupPort;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    public MemberRegisterService(EmailVerificationApi emailVerificationApi,
                                  MemberRepository memberRepository,
                                  TermsAgreementRepository termsAgreementRepository,
                                  SchoolLookupPort schoolLookupPort,
                                  PasswordEncoder passwordEncoder,
                                  TokenIssuer tokenIssuer) {
        this.emailVerificationApi = emailVerificationApi;
        this.memberRepository = memberRepository;
        this.termsAgreementRepository = termsAgreementRepository;
        this.schoolLookupPort = schoolLookupPort;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
    }

    public record Command(String verificationToken, String name, String password, List<Agreement> agreements) {

        public record Agreement(TermsType type, String version, boolean agreed) {
        }
    }

    public record Result(Long memberId, String name, String email, SchoolLookupPort.SchoolInfo school,
                         TokenIssuer.TokenPair tokens) {
    }

    /** 회원 · 약관 · 토큰 사용 처리는 한 트랜잭션 (명세 메모). 하나라도 실패하면 인증 토큰도 쓰이지 않은 상태로 남는다. */
    @Transactional
    public Result register(Command command) {
        if (!PasswordPolicy.isValid(command.password())) {
            throw new BusinessException(MemberErrorCode.PASSWORD_POLICY_VIOLATION);
        }
        requireAllRequiredTermsAgreed(command.agreements());

        // 이메일은 요청에서 받지 않는다 — 인증 토큰에 묶인 것을 쓴다 (다른 이메일로 바꿔치기 방지).
        EmailVerificationApi.VerifiedEmail verified =
                emailVerificationApi.consume(command.verificationToken(), VerificationPurpose.SIGNUP);

        if (memberRepository.findByEmail(verified.email()).isPresent()) {
            throw new BusinessException(MemberErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        Member member = Member.register(verified.schoolId(), verified.email(),
                passwordEncoder.encode(command.password()), command.name());
        try {
            // 위의 조회와 저장 사이에 같은 이메일이 들어올 수 있다. 최종 방어는 uk_member_email이다.
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(MemberErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        Instant now = Instant.now();
        command.agreements().stream()
                .filter(Command.Agreement::agreed)
                .map(agreement -> TermsAgreement.of(member.getId(), agreement.type(), agreement.version(), now))
                .forEach(termsAgreementRepository::save);

        SchoolLookupPort.SchoolInfo school = schoolLookupPort.findById(verified.schoolId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        // 토큰을 만드는 코드는 auth 뒤에만 있다 — 회원 모듈은 JWT를 모른다 (rules.py AUTH_SPEC).
        TokenIssuer.TokenPair tokens = tokenIssuer.issue(new AuthUser(
                member.getId(), AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, member.getSchoolId(), null));

        return new Result(member.getId(), member.getName(), member.getEmail(), school, tokens);
    }

    private void requireAllRequiredTermsAgreed(List<Command.Agreement> agreements) {
        Set<TermsType> agreed = agreements.stream()
                .filter(Command.Agreement::agreed)
                .map(Command.Agreement::type)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(TermsType.class)));
        if (!agreed.containsAll(REQUIRED_TERMS)) {
            throw new BusinessException(MemberErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
    }
}
