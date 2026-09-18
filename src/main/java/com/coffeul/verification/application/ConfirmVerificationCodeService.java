package com.coffeul.verification.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.verification.domain.EmailVerification;
import com.coffeul.verification.api.VerificationPurpose;
import com.coffeul.verification.infrastructure.EmailVerificationRepository;
import com.coffeul.verification.infrastructure.VerificationHasher;
import com.coffeul.verification.infrastructure.VerificationTokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/** SM-2(인증코드 확인) — REQ-EV-004 · 005. */
@Service
public class ConfirmVerificationCodeService {

    private final EmailVerificationRepository verificationRepository;
    private final VerificationProperties properties;

    public ConfirmVerificationCodeService(EmailVerificationRepository verificationRepository,
                                           VerificationProperties properties) {
        this.verificationRepository = verificationRepository;
        this.properties = properties;
    }

    public record Result(String verificationToken, long expiresInSeconds) {
    }

    /**
     * BusinessException은 실패 횟수를 이미 올린 뒤에 던지는 정상 흐름이라 롤백 대상에서 뺀다 —
     * 안 그러면 5회 폐기(REQ-EV-004)가 매번 롤백돼서 무한히 코드를 찍어볼 수 있다.
     * (auth 모듈이 로그인 실패 카운트에서 같은 함정을 밟았다.)
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Result confirm(String rawEmail, VerificationPurpose purpose, String code) {
        String email = rawEmail.trim().toLowerCase();
        Instant now = Instant.now();

        // 용도가 섞이지 않도록 (email, purpose)로 찾는다 (REQ-EV-005).
        EmailVerification verification = verificationRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new BusinessException(VerificationErrorCode.CODE_EXPIRED));

        // 요청 이력 없음 · 만료 · 5회 폐기 · 이미 사용됨은 모두 같은 응답으로 묶인다 (명세의 ❌ 실패 2).
        if (verification.isUnusable(now, properties.maxAttempts())) {
            throw new BusinessException(VerificationErrorCode.CODE_EXPIRED);
        }

        if (!verification.matches(VerificationHasher.sha256Hex(code))) {
            verification.recordFailedAttempt();
            if (verification.getAttemptCount() >= properties.maxAttempts()) {
                verification.discard(now);
                throw new BusinessException(VerificationErrorCode.CODE_EXPIRED);
            }
            throw BusinessException.withData(VerificationErrorCode.CODE_MISMATCH,
                    VerificationErrorCode.CODE_MISMATCH.message(),
                    Map.of("remainingAttempts", verification.remainingAttempts(properties.maxAttempts())));
        }

        String token = VerificationTokenGenerator.generate();
        verification.issueToken(VerificationHasher.sha256Hex(token), now, properties.tokenTtl());
        return new Result(token, properties.tokenTtlSeconds());
    }
}
