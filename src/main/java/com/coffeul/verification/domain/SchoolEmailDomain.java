package com.coffeul.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * schema.sql `school_email_domain` 테이블 매핑 (REQ-EV-001).
 * 학교는 코드가 아니라 데이터다 — 새 학교를 받으려면 이 테이블에 행을 추가하면 되고 배포가 필요 없다.
 */
@Entity
@Table(name = "school_email_domain")
public class SchoolEmailDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "domain", nullable = false, length = 100)
    private String domain;

    protected SchoolEmailDomain() {
    }

    public Long getSchoolId() {
        return schoolId;
    }

    public String getDomain() {
        return domain;
    }
}
