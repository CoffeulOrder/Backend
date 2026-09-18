package com.coffeul.verification.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 인증코드의 시도 횟수 · 만료 판정 (REQ-EV-004 · 005). DB · Spring 없이 도는 단위 테스트. */
class EmailVerificationTest {

    private static final int MAX_ATTEMPTS = 5;
    private static final Instant NOW = Instant.parse("2026-10-16T03:00:00Z");

    private EmailVerification issued() {
        return EmailVerification.issue("student@g.eulji.ac.kr", VerificationPurpose.SIGNUP,
                "hash", NOW, Duration.ofMinutes(5));
    }

    @Test
    void 발급_직후에는_쓸_수_있고_남은_시도는_5회다() {
        EmailVerification verification = issued();

        assertThat(verification.isUnusable(NOW, MAX_ATTEMPTS)).isFalse();
        assertThat(verification.remainingAttempts(MAX_ATTEMPTS)).isEqualTo(5);
    }

    @Test
    void 만료_시각이_지나면_쓸_수_없다() {
        EmailVerification verification = issued();

        assertThat(verification.isUnusable(NOW.plus(Duration.ofMinutes(5)), MAX_ATTEMPTS)).isTrue();
    }

    @Test
    void 코드를_5회_틀리면_만료_시각과_상관없이_쓸_수_없다() {
        EmailVerification verification = issued();

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            verification.recordFailedAttempt();
        }

        assertThat(verification.remainingAttempts(MAX_ATTEMPTS)).isZero();
        assertThat(verification.isUnusable(NOW, MAX_ATTEMPTS)).isTrue();
    }

    @Test
    void 폐기하면_만료_시각이_지금으로_당겨진다() {
        EmailVerification verification = issued();

        verification.discard(NOW);

        assertThat(verification.getExpiresAt()).isEqualTo(NOW);
        assertThat(verification.isUnusable(NOW, MAX_ATTEMPTS)).isTrue();
    }

    @Test
    void 토큰을_다시_발급하면_이전_토큰은_덮어써진다() {
        EmailVerification verification = issued();

        verification.issueToken("first", NOW, Duration.ofMinutes(30));
        verification.issueToken("second", NOW.plusSeconds(1), Duration.ofMinutes(30));

        assertThat(verification.getVerifiedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(verification.isUnusable(NOW.plusSeconds(1), MAX_ATTEMPTS)).isFalse();
    }

    @Test
    void 코드_해시가_같아야_일치로_본다() {
        EmailVerification verification = issued();

        assertThat(verification.matches("hash")).isTrue();
        assertThat(verification.matches("other")).isFalse();
    }
}
