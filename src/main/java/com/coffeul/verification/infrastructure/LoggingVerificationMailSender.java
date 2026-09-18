package com.coffeul.verification.infrastructure;

import com.coffeul.verification.application.VerificationMailSender;
import com.coffeul.verification.domain.VerificationPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 개발 · 테스트용 가짜 메일 어댑터. 인증코드를 로그로만 남긴다.
 * <p>실제 발송(SES)은 도메인 확보와 샌드박스 해제가 끝나야 붙일 수 있어서 아직 없다.
 * MAIL_PROVIDER=ses로 두면 이 빈이 안 뜨고, 그때 SesVerificationMailSender가 대신 들어온다.
 */
@Component
@ConditionalOnProperty(name = "coffeul.verification.mail-provider", havingValue = "log", matchIfMissing = true)
public class LoggingVerificationMailSender implements VerificationMailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationMailSender.class);

    @Override
    public void send(String email, VerificationPurpose purpose, String code) {
        log.info("[가짜 메일] to={} purpose={} code={}", email, purpose, code);
    }
}
