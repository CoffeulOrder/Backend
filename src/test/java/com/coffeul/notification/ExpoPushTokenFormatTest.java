package com.coffeul.notification;

import com.coffeul.notification.domain.ExpoPushTokenFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpoPushTokenFormatTest {

    @Test
    void Exponent_접두어_토큰은_통과한다() {
        assertThat(ExpoPushTokenFormat.isValid("ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]")).isTrue();
    }

    @Test
    void Expo_접두어_토큰도_통과한다() {
        assertThat(ExpoPushTokenFormat.isValid("ExpoPushToken[abc-123_DEF]")).isTrue();
    }

    @Test
    void null_빈값_공백은_거부한다() {
        assertThat(ExpoPushTokenFormat.isValid(null)).isFalse();
        assertThat(ExpoPushTokenFormat.isValid("")).isFalse();
        assertThat(ExpoPushTokenFormat.isValid("   ")).isFalse();
    }

    @Test
    void 접두어나_대괄호가_틀리면_거부한다() {
        assertThat(ExpoPushTokenFormat.isValid("fcm-token-123")).isFalse();
        assertThat(ExpoPushTokenFormat.isValid("ExponentPushToken[]")).isFalse();
        assertThat(ExpoPushTokenFormat.isValid("ExponentPushToken[abc")).isFalse();
        assertThat(ExpoPushTokenFormat.isValid("ExponentPushToken[a[b]c]")).isFalse();
        assertThat(ExpoPushTokenFormat.isValid("exponentpushtoken[abc]")).isFalse();
    }

    @Test
    void 안에_공백이_있거나_뒤에_뭔가_붙으면_거부한다() {
        assertThat(ExpoPushTokenFormat.isValid("ExponentPushToken[ab c]")).isFalse();
        assertThat(ExpoPushTokenFormat.isValid("ExponentPushToken[abc] ")).isFalse();
        assertThat(ExpoPushTokenFormat.isValid("ExponentPushToken[abc]\n")).isFalse();
    }

    @Test
    void 컬럼_길이_255자를_넘으면_거부한다() {
        String prefix = "ExponentPushToken[";
        String atLimit = prefix + "a".repeat(255 - prefix.length() - 1) + "]";
        assertThat(atLimit).hasSize(255);
        assertThat(ExpoPushTokenFormat.isValid(atLimit)).isTrue();
        assertThat(ExpoPushTokenFormat.isValid(prefix + "a".repeat(255 - prefix.length()) + "]")).isFalse();
    }
}
