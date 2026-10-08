package com.coffeul.member.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 비밀번호 규칙 (REQ-U-002). DB · Spring 없이 도는 단위 테스트. */
class PasswordPolicyTest {

    @Test
    void 영문과_숫자를_포함한_8자_이상이면_통과한다() {
        assertThat(PasswordPolicy.isValid("coffee1234")).isTrue();
        assertThat(PasswordPolicy.isValid("a1234567")).isTrue();
    }

    @Test
    void 길이가_8자보다_짧으면_거부한다() {
        assertThat(PasswordPolicy.isValid("cof1234")).isFalse();
    }

    @Test
    void 숫자만_있거나_영문만_있으면_거부한다() {
        assertThat(PasswordPolicy.isValid("123456789")).isFalse();
        assertThat(PasswordPolicy.isValid("coffeelatte")).isFalse();
    }

    @Test
    void 비어_있으면_거부한다() {
        assertThat(PasswordPolicy.isValid(null)).isFalse();
        assertThat(PasswordPolicy.isValid("")).isFalse();
    }

    @Test
    void 특수문자가_섞여도_영문과_숫자가_있으면_통과한다() {
        assertThat(PasswordPolicy.isValid("coffee!1234")).isTrue();
    }

    @Test
    void 길이가_72바이트를_넘으면_거부한다() {
        // BCrypt가 73바이트째부터 조용히 잘라내서, 넘겨서 저장하면 입력과 검사 대상이 달라진다.
        String seventyThreeBytes = "a1".repeat(36) + "b";
        assertThat(seventyThreeBytes.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isEqualTo(73);
        assertThat(PasswordPolicy.isValid(seventyThreeBytes)).isFalse();

        String seventyTwoBytes = "a1".repeat(36);
        assertThat(PasswordPolicy.isValid(seventyTwoBytes)).isTrue();
    }

    @Test
    void 한글은_한_글자가_3바이트라_24글자를_넘으면_거부한다() {
        String twentyFiveHangul = "가".repeat(24) + "a1";
        assertThat(PasswordPolicy.isValid(twentyFiveHangul)).isFalse();
    }
}
