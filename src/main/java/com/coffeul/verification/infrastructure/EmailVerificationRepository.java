package com.coffeul.verification.infrastructure;

import com.coffeul.verification.domain.EmailVerification;
import com.coffeul.verification.api.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    /** 재발송 제한(REQ-EV-003)은 용도와 무관하게 "같은 이메일" 기준이다. */
    Optional<EmailVerification> findTopByEmailOrderByCreatedAtDesc(String email);

    long countByEmailAndCreatedAtGreaterThanEqual(String email, Instant from);

    Optional<EmailVerification> findTopByEmailAndPurposeOrderByCreatedAtDesc(String email, VerificationPurpose purpose);

    Optional<EmailVerification> findByTokenHash(String tokenHash);

    /** 정리 작업(매일 04:00 KST)은 한 번에 100건씩만 지운다 (rules.py 스케줄 작업 규칙). */
    List<EmailVerification> findTop100ByExpiresAtLessThan(Instant threshold);
}
