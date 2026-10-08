package com.coffeul.notification.domain;

import java.util.regex.Pattern;

/**
 * Expo 푸시 토큰 형식 검사 — {@code ExponentPushToken[...]} 또는 {@code ExpoPushToken[...]}.
 * 대괄호 안은 Expo가 정하는 값이라 내용은 보지 않고, 공백 · 대괄호가 없는 비어 있지 않은 문자열인지만 본다.
 * device_token.expo_push_token이 VARCHAR(255)라 길이도 여기서 막는다.
 */
public final class ExpoPushTokenFormat {

    private static final int MAX_LENGTH = 255;
    private static final Pattern PATTERN = Pattern.compile("^Expo(nent)?PushToken\\[[^\\[\\]\\s]+]$");

    private ExpoPushTokenFormat() {
    }

    public static boolean isValid(String token) {
        return token != null && token.length() <= MAX_LENGTH && PATTERN.matcher(token).matches();
    }
}
