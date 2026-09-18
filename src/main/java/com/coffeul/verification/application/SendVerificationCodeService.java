package com.coffeul.verification.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.verification.api.MemberAccountPort;
import com.coffeul.verification.domain.EmailVerification;
import com.coffeul.verification.api.VerificationPurpose;
import com.coffeul.verification.infrastructure.EmailVerificationRepository;
import com.coffeul.verification.infrastructure.SchoolEmailDomainRepository;
import com.coffeul.verification.infrastructure.VerificationCodeGenerator;
import com.coffeul.verification.infrastructure.VerificationHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/** SM-1(인증코드 발송) — REQ-EV-001 · 002 · 003 · 006 · 007. */
@Service
public class SendVerificationCodeService {

    private static final Logger log = LoggerFactory.getLogger(SendVerificationCodeService.class);

    private final EmailVerificationRepository verificationRepository;
    private final SchoolEmailDomainRepository schoolEmailDomainRepository;
    private final MemberAccountPort memberAccountPort;
    private final VerificationMailSender mailSender;
    private final VerificationProperties properties;
    private final ZoneId businessZone;

    public SendVerificationCodeService(EmailVerificationRepository verificationRepository,
                                        SchoolEmailDomainRepository schoolEmailDomainRepository,
                                        MemberAccountPort memberAccountPort,
                                        VerificationMailSender mailSender,
                                        VerificationProperties properties,
                                        @Value("${coffeul.business-zone:Asia/Seoul}") String businessZone) {
        this.verificationRepository = verificationRepository;
        this.schoolEmailDomainRepository = schoolEmailDomainRepository;
        this.memberAccountPort = memberAccountPort;
        this.mailSender = mailSender;
        this.properties = properties;
        this.businessZone = ZoneId.of(businessZone);
    }

    public record Result(String email, long expiresInSeconds, long resendAvailableInSeconds) {
    }

    /**
     * 메일 발송이 실패하면 예외로 트랜잭션을 되돌려 코드 행을 남기지 않는다 (REQ-EV-007).
     * 그래서 여기서는 noRollbackFor를 쓰지 않는다 — 발송 실패 시 롤백이 곧 명세다.
     */
    @Transactional
    public Result send(String rawEmail, VerificationPurpose purpose) {
        String email = normalize(rawEmail);
        Instant now = Instant.now();

        requireRegisteredSchoolDomain(email);
        checkThrottle(email, now);

        boolean registered = memberAccountPort.existsByEmail(email);
        if (purpose == VerificationPurpose.SIGNUP && registered) {
            throw new BusinessException(VerificationErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        // 가입되지 않은 이메일로 비밀번호를 재설정하려 하면 가입 여부를 숨기려고 똑같이 200을 주고 메일만 보내지 않는다 (명세 메모).
        // 단 행은 그대로 만든다 — 안 그러면 "가입된 이메일만 60초 제한에 걸린다"는 차이로 가입 여부가 그대로 드러난다.
        boolean deliver = purpose != VerificationPurpose.PASSWORD_RESET || registered;

        String code = VerificationCodeGenerator.generate();
        verificationRepository.save(EmailVerification.issue(
                email, purpose, VerificationHasher.sha256Hex(code), now, properties.codeTtl()));

        if (deliver) {
            deliver(email, purpose, code);
        } else {
            log.info("인증코드 발송 생략 — 가입되지 않은 이메일의 비밀번호 재설정 요청 (purpose={})", purpose);
        }

        return new Result(email, properties.codeTtlSeconds(), properties.resendIntervalSeconds());
    }

    private void deliver(String email, VerificationPurpose purpose, String code) {
        try {
            mailSender.send(email, purpose, code);
        } catch (VerificationMailSender.MailDeliveryException e) {
            log.error("인증코드 메일 발송 실패 (purpose={})", purpose, e);
            throw new BusinessException(VerificationErrorCode.MAIL_DELIVERY_FAILED);
        }
    }

    private void requireRegisteredSchoolDomain(String email) {
        int at = email.lastIndexOf('@');
        String domain = at < 0 ? "" : email.substring(at + 1);
        if (schoolEmailDomainRepository.findByDomain(domain).isEmpty()) {
            throw new BusinessException(VerificationErrorCode.UNSUPPORTED_SCHOOL_DOMAIN);
        }
    }

    private void checkThrottle(String email, Instant now) {
        Optional<EmailVerification> latest = verificationRepository.findTopByEmailOrderByCreatedAtDesc(email);
        if (latest.isPresent()) {
            Instant availableAt = latest.get().getCreatedAt().plus(properties.resendInterval());
            if (availableAt.isAfter(now)) {
                long retryAfterSeconds = Math.max(1, Duration.between(now, availableAt).toSeconds());
                throw BusinessException.withData(VerificationErrorCode.RESEND_TOO_SOON,
                        VerificationErrorCode.RESEND_TOO_SOON.message(),
                        Map.of("retryAfterSeconds", retryAfterSeconds));
            }
        }

        // "하루 10회"는 영업 시간대(Asia/Seoul) 자정 기준이다 — 응답 문구가 "오늘은"이라 사용자가 기대하는 것도 그쪽이다.
        Instant startOfToday = LocalDate.now(businessZone).atStartOfDay(businessZone).toInstant();
        if (verificationRepository.countByEmailAndCreatedAtGreaterThanEqual(email, startOfToday)
                >= properties.dailySendLimit()) {
            throw new BusinessException(VerificationErrorCode.DAILY_LIMIT_EXCEEDED);
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
