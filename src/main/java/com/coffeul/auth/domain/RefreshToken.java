package com.coffeul.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * schema.sql `refresh_token` 테이블 매핑. JWT가 아니다 — 무작위 문자열의 해시만 저장한다.
 * <p>{@code token_hash}(CHAR(64))·{@code family_id}(CHAR(36))는 고정 길이 컬럼이라 명시적으로
 * columnDefinition을 줘야 한다 — 안 그러면 Hibernate가 VARCHAR로 추론해 실제 MySQL 스키마 검증에서 깨진다.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_type", nullable = false, length = 10)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "token_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "family_id", nullable = false, columnDefinition = "CHAR(36)")
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {
    }

    public static RefreshToken issue(String subjectType, Long subjectId, String tokenHash,
                                      String familyId, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.subjectType = subjectType;
        token.subjectId = subjectId;
        token.tokenHash = tokenHash;
        token.familyId = familyId;
        token.expiresAt = expiresAt;
        return token;
    }

    /** 정상 회전(재발급). 30초 유예 안에서는 여전히 "동시 요청"으로 다시 쓸 수 있게 만료는 그대로 둔다. */
    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    /**
     * 탈취로 판단해 계열을 통째로 폐기할 때. 만료 시각도 같이 지금으로 당겨서,
     * 이 토큰이 나중에 30초 유예 창 안에서 다시 오더라도 "동시 요청"으로 오인하지 않고 즉시 막는다.
     */
    public void revokeDueToReuse(Instant now) {
        this.revokedAt = now;
        this.expiresAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
