package com.coffeul.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/**
 * schema.sql `email_verification` 테이블 매핑 (REQ-EV-002 · 004 · 005).
 * 코드와 인증 토큰은 평문으로 두지 않고 SHA-256 해시만 저장한다.
 * <p>{@code code_hash}·{@code token_hash}는 CHAR(64), {@code attempt_count}는 TINYINT라
 * columnDefinition과 byte를 명시한다 — 안 그러면 Hibernate가 VARCHAR·INT로 추론해
 * 실제 MySQL 스키마 검증(ddl-auto=validate)에서 깨진다.
 */
@Entity
@Table(name = "email_verification")
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private VerificationPurpose purpose;

    @Column(name = "code_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private byte attemptCount;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "token_hash", columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    // DB에도 기본값이 있지만 코드에서 채운다 — 재발송 제한(REQ-EV-003)이 이 값을 직접 조회하기 때문에
    // 저장 직후 영속성 컨텍스트에서도 값이 보여야 한다.
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailVerification() {
    }

    public static EmailVerification issue(String email, VerificationPurpose purpose, String codeHash,
                                           Instant now, Duration codeTtl) {
        EmailVerification verification = new EmailVerification();
        verification.email = email;
        verification.purpose = purpose;
        verification.codeHash = codeHash;
        verification.expiresAt = now.plus(codeTtl);
        verification.attemptCount = 0;
        verification.createdAt = now;
        return verification;
    }

    /** 만료 · 폐기 · 이미 사용됨 — 전부 "다시 요청해주세요"(EV005)로 묶인다. */
    public boolean isUnusable(Instant now, int maxAttempts) {
        return consumedAt != null || !expiresAt.isAfter(now) || attemptCount >= maxAttempts;
    }

    public boolean matches(String candidateCodeHash) {
        return codeHash.equals(candidateCodeHash);
    }

    public void recordFailedAttempt() {
        this.attemptCount++;
    }

    /** 코드를 더 못 쓰게 만든다. 만료 시각을 지금으로 당겨서 재검증 · 정리 작업이 같은 조건으로 걸리게 한다. */
    public void discard(Instant now) {
        this.expiresAt = now;
    }

    public int remainingAttempts(int maxAttempts) {
        return Math.max(0, maxAttempts - attemptCount);
    }

    /**
     * 검증 성공 → 인증 토큰 발급. 같은 코드로 다시 확인하면(앱이 첫 응답을 못 받고 재시도하는 경우)
     * 새 토큰으로 덮어쓴다 — 이전 토큰 해시가 사라지므로 살아 있는 토큰은 언제나 하나뿐이다.
     */
    public void issueToken(String tokenHash, Instant now, Duration tokenTtl) {
        this.verifiedAt = now;
        this.tokenHash = tokenHash;
        this.tokenExpiresAt = now.plus(tokenTtl);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public VerificationPurpose getPurpose() {
        return purpose;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public byte getAttemptCount() {
        return attemptCount;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }
}
