package com.coffeul.verification.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.verification.api.VerificationErrorCode;
import com.coffeul.verification.api.EmailVerificationApi;
import com.coffeul.verification.api.VerificationPurpose;
import com.coffeul.verification.domain.EmailVerification;
import com.coffeul.verification.infrastructure.EmailVerificationRepository;
import com.coffeul.verification.infrastructure.SchoolEmailDomainRepository;
import com.coffeul.verification.infrastructure.VerificationHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 인증 토큰 소비 (REQ-EV-005). member의 SM-3 · SM-6이 호출한다.
 * <p>호출자의 트랜잭션에 참여한다 — 가입이 실패하면 토큰도 쓰이지 않은 상태로 돌아가야 하기 때문이다.
 */
@Service
public class EmailVerificationApiImpl implements EmailVerificationApi {

    private final EmailVerificationRepository verificationRepository;
    private final SchoolEmailDomainRepository schoolEmailDomainRepository;

    public EmailVerificationApiImpl(EmailVerificationRepository verificationRepository,
                                     SchoolEmailDomainRepository schoolEmailDomainRepository) {
        this.verificationRepository = verificationRepository;
        this.schoolEmailDomainRepository = schoolEmailDomainRepository;
    }

    @Override
    @Transactional
    public VerifiedEmail consume(String verificationToken, VerificationPurpose purpose) {
        Instant now = Instant.now();
        EmailVerification verification = verificationRepository
                .findByTokenHash(VerificationHasher.sha256Hex(verificationToken))
                .orElseThrow(() -> new BusinessException(VerificationErrorCode.VERIFICATION_TOKEN_INVALID));

        // 만료 · 이미 사용됨 · 용도 불일치를 하나로 묶는다 (명세: EV008).
        if (!verification.isTokenUsable(now, purpose)) {
            throw new BusinessException(VerificationErrorCode.VERIFICATION_TOKEN_INVALID);
        }

        verification.consume(now);

        String email = verification.getEmail();
        Long schoolId = schoolEmailDomainRepository.findByDomain(domainOf(email))
                .map(domain -> domain.getSchoolId())
                // 코드를 보낼 땐 있었는데 그사이 학교 도메인이 내려간 경우. 가입을 진행할 근거가 없다.
                .orElseThrow(() -> new BusinessException(VerificationErrorCode.UNSUPPORTED_SCHOOL_DOMAIN));

        return new VerifiedEmail(email, schoolId);
    }

    private String domainOf(String email) {
        int at = email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1);
    }
}
