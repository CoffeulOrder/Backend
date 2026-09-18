package com.coffeul.support;

import com.coffeul.verification.api.VerificationPurpose;
import com.coffeul.verification.application.VerificationMailSender;

/**
 * 메일은 포트 뒤에 있으므로 테스트는 가짜 어댑터로 돈다 (rules.py DOD).
 * 인증코드 평문을 꺼낼 수 있는 유일한 곳이라, 가입 · 비밀번호 재설정 테스트도 이걸 거쳐 토큰을 얻는다.
 */
public class RecordingMailSender implements VerificationMailSender {

    public String lastEmail;
    public String lastCode;
    public VerificationPurpose lastPurpose;
    public int sendCount;
    public boolean failNext;

    @Override
    public void send(String email, VerificationPurpose purpose, String code) {
        if (failNext) {
            throw new MailDeliveryException("테스트용 발송 실패", null);
        }
        this.lastEmail = email;
        this.lastPurpose = purpose;
        this.lastCode = code;
        this.sendCount++;
    }

    public void reset() {
        lastEmail = null;
        lastCode = null;
        lastPurpose = null;
        sendCount = 0;
        failNext = false;
    }
}
