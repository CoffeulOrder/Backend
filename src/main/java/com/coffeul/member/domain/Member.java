package com.coffeul.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** schema.sql `member` 테이블 매핑 (일부 컬럼만 — 기준 구현 범위. 주문 이력 · 푸시 토큰 등은 아직 없다). */
@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    // DB 기본값(CURRENT_TIMESTAMP(6))이 채운다. SM-4 응답의 createdAt용으로 읽기만 한다.
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Member() {
    }

    /** SM-3 가입 (REQ-U-001). 학교는 인증 토큰에 묶인 이메일 도메인으로 이미 정해져서 넘어온다. */
    public static Member register(Long schoolId, String email, String passwordHash, String name) {
        Member member = new Member();
        member.schoolId = schoolId;
        member.email = email;
        member.passwordHash = passwordHash;
        member.name = name;
        member.status = "ACTIVE";
        return member;
    }

    public Long getId() {
        return id;
    }

    public Long getSchoolId() {
        return schoolId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void recordLoginSuccess(Instant now) {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
    }

    /**
     * 비밀번호 재설정 · 변경 (REQ-U-005 · 006). 로그인 잠금도 같이 푼다 —
     * 5회 틀려서 잠긴 사람이 비밀번호를 재설정하고도 15분을 더 기다려야 하면 재설정한 의미가 없다.
     */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    /**
     * SM-7 탈퇴 (REQ-U-007). 개인정보만 지우고 행은 남긴다 — 주문 · 결제 기록이 member_id로 이 행을 가리키고
     * 전자상거래법 시행령 제6조가 계약 · 대금결제 기록을 5년 보존하도록 정하고 있어서, 행을 지우면 그 기록이
     * 끊긴다. email을 NULL로 두면 uk_member_email에 걸리지 않으므로 같은 이메일로 다시 가입할 수 있다.
     */
    public void withdraw(Instant now) {
        this.email = null;
        this.passwordHash = null;
        this.name = "탈퇴회원";
        this.status = "WITHDRAWN";
        this.withdrawnAt = now;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public void recordLoginFailure(Instant lockedUntil) {
        this.failedLoginCount = this.failedLoginCount + 1;
        if (lockedUntil != null) {
            this.lockedUntil = lockedUntil;
        }
    }
}
