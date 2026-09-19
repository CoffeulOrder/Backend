package com.coffeul.verification.infrastructure;

import com.coffeul.verification.api.VerificationPurpose;
import com.coffeul.verification.application.VerificationMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

/**
 * 실제 인증코드 메일 발송 (Amazon SES v2). {@code MAIL_PROVIDER=ses}일 때만 뜬다.
 *
 * <p>로그 어댑터가 비워둔 자리를 그대로 채운다 — 포트({@link VerificationMailSender})도, 이 어댑터를 쓰는
 * {@code SendVerificationCodeService}도 바뀌지 않는다.
 *
 * <p><b>운영 전 반드시 확인할 것</b>: 샌드박스 상태에서는 <b>검증된 주소로만</b> 발송된다. 해제 전에
 * {@code MAIL_PROVIDER=ses}로 올리면 실제 학생 메일이 전부 EV007로 막힌다 — 즉 가입이 막힌다.
 * 해제가 끝난 뒤에 전환한다.
 *
 * <p>본문을 HTML 없이 텍스트로만 보낸다. 인증코드 한 줄이라 HTML이 얻는 게 없고, 텍스트 전용 메일이
 * 스팸 판정에서 유리하다 — 학교 메일함(Google Workspace)에서 스팸으로 가면 서비스가 통째로 막힌다.
 */
@Component
@ConditionalOnProperty(name = "coffeul.verification.mail-provider", havingValue = "ses")
public class SesVerificationMailSender implements VerificationMailSender {

    private static final Logger log = LoggerFactory.getLogger(SesVerificationMailSender.class);
    private static final String CHARSET = "UTF-8";

    private final SesV2Client sesClient;
    private final String from;
    private final int codeTtlMinutes;

    public SesVerificationMailSender(SesV2Client sesClient,
                                      @Value("${coffeul.verification.mail-from}") String from,
                                      @Value("${coffeul.verification.code-ttl-seconds}") int codeTtlSeconds) {
        this.sesClient = sesClient;
        this.from = from;
        this.codeTtlMinutes = Math.max(1, codeTtlSeconds / 60);
    }

    @Override
    public void send(String email, VerificationPurpose purpose, String code) {
        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(from)
                .destination(Destination.builder().toAddresses(email).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(text(subjectOf(purpose)))
                                .body(Body.builder().text(text(bodyOf(purpose, code))).build())
                                .build())
                        .build())
                .build();

        try {
            String messageId = sesClient.sendEmail(request).messageId();
            // 코드 자체는 절대 로그에 남기지 않는다 — 로그를 보는 사람이 남의 계정에 들어갈 수 있게 된다.
            log.info("인증코드 메일 발송 to={} purpose={} messageId={}", masked(email), purpose, messageId);
        } catch (SesV2Exception e) {
            // 호출자가 EV007로 바꾸고 코드 행을 저장하지 않는다 — 메일이 안 갔는데 "보냈다"가 되면
            // 사용자는 오지 않는 코드를 기다리다가 재발송 제한(60초)에까지 걸린다.
            throw new MailDeliveryException("SES 발송 실패: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    /** 한글 제목 · 본문이 깨지지 않게 charset을 명시한다 — 빼면 SES가 ASCII로 보고 물음표가 된다. */
    private Content text(String value) {
        return Content.builder().data(value).charset(CHARSET).build();
    }

    private String subjectOf(VerificationPurpose purpose) {
        return purpose == VerificationPurpose.PASSWORD_RESET
                ? "[Coffeul] 비밀번호 재설정 인증코드"
                : "[Coffeul] 회원가입 인증코드";
    }

    private String bodyOf(VerificationPurpose purpose, String code) {
        String what = purpose == VerificationPurpose.PASSWORD_RESET ? "비밀번호 재설정" : "회원가입";
        return """
                Coffeul %s 인증코드예요.

                    %s

                %d분 안에 입력해주세요.
                본인이 요청한 게 아니면 이 메일은 무시하셔도 됩니다.
                """.formatted(what, code, codeTtlMinutes);
    }

    /** 로그에 이메일 전체를 남기지 않는다 (개인정보). st***@g.eulji.ac.kr 형태. */
    private String masked(String email) {
        int at = email.indexOf('@');
        if (at <= 2) {
            return "***" + (at < 0 ? "" : email.substring(at));
        }
        return email.substring(0, 2) + "***" + email.substring(at);
    }
}
