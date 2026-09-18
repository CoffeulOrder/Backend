package com.coffeul.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * schema.sql `terms_agreement` 테이블 매핑 (REQ-U-003).
 * 어떤 버전에 언제 동의했는지가 분쟁 시 증거라서, 동의 여부가 아니라 버전과 시각을 남긴다.
 */
@Entity
@Table(name = "terms_agreement")
public class TermsAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", nullable = false, length = 20)
    private TermsType termsType;

    @Column(name = "terms_version", nullable = false, length = 20)
    private String termsVersion;

    @Column(name = "agreed_at", nullable = false)
    private Instant agreedAt;

    protected TermsAgreement() {
    }

    public static TermsAgreement of(Long memberId, TermsType termsType, String termsVersion, Instant agreedAt) {
        TermsAgreement agreement = new TermsAgreement();
        agreement.memberId = memberId;
        agreement.termsType = termsType;
        agreement.termsVersion = termsVersion;
        agreement.agreedAt = agreedAt;
        return agreement;
    }

    public Long getId() {
        return id;
    }

    public TermsType getTermsType() {
        return termsType;
    }

    public String getTermsVersion() {
        return termsVersion;
    }
}
