package com.coffeul.verification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 이메일 인증 정책 값 (rules.py 설정 키: "코드에 숫자를 박지 않는다").
 * 키 이름은 application.yml의 다른 정책 값(access-token-expire-minutes 등)과 같은 방식으로 단위를 뒤에 붙인다.
 * 기본값은 명세(REQ-EV-002 · 003 · 004)의 숫자와 같다.
 */
@ConfigurationProperties(prefix = "coffeul.verification")
public record VerificationProperties(
        long codeTtlSeconds,
        long tokenTtlSeconds,
        long resendIntervalSeconds,
        int dailySendLimit,
        int maxAttempts,
        int retentionDays
) {

    public VerificationProperties {
        codeTtlSeconds = codeTtlSeconds > 0 ? codeTtlSeconds : 300;
        tokenTtlSeconds = tokenTtlSeconds > 0 ? tokenTtlSeconds : 1800;
        resendIntervalSeconds = resendIntervalSeconds > 0 ? resendIntervalSeconds : 60;
        dailySendLimit = dailySendLimit > 0 ? dailySendLimit : 10;
        maxAttempts = maxAttempts > 0 ? maxAttempts : 5;
        retentionDays = retentionDays > 0 ? retentionDays : 7;
    }

    public Duration codeTtl() {
        return Duration.ofSeconds(codeTtlSeconds);
    }

    public Duration tokenTtl() {
        return Duration.ofSeconds(tokenTtlSeconds);
    }

    public Duration resendInterval() {
        return Duration.ofSeconds(resendIntervalSeconds);
    }

    public Duration retention() {
        return Duration.ofDays(retentionDays);
    }
}
