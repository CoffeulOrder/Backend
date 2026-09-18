package com.coffeul.verification.application;

import com.coffeul.verification.domain.VerificationPurpose;

/**
 * 메일 발송 포트 (rules.py DOD: "외부 호출(PG · S3 · 메일 · Expo)은 포트 뒤에 있고 테스트는 가짜 어댑터로 돈다").
 * 실제 발송 수단(SES 샌드박스 해제 · 도메인)은 인프라 작업이라 아직 로그 어댑터만 있다.
 */
public interface VerificationMailSender {

    /** 발송에 실패하면 {@link MailDeliveryException}을 던진다 — 호출자가 EV007로 바꾸고 코드를 저장하지 않는다. */
    void send(String email, VerificationPurpose purpose, String code);

    class MailDeliveryException extends RuntimeException {

        public MailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
