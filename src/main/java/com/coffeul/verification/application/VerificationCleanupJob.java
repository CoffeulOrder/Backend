package com.coffeul.verification.application;

import com.coffeul.verification.domain.EmailVerification;
import com.coffeul.verification.infrastructure.EmailVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 인증코드 정리 (rules.py 스케줄 작업: verification · 매일 04:00 KST · 만료 후 7일 지난 행 삭제).
 * 삭제라서 몇 번 돌아도 결과가 같다. 한 번에 100건씩만 지운다 — 한 번의 실행이 테이블을 오래 잡지 않게.
 */
@Component
public class VerificationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(VerificationCleanupJob.class);

    /** 한 번 실행에서 도는 배치 수의 상한. 밀린 게 남아도 다음 날 실행이 이어서 지운다. */
    private static final int MAX_BATCHES = 100;

    private final EmailVerificationRepository verificationRepository;
    private final VerificationProperties properties;

    public VerificationCleanupJob(EmailVerificationRepository verificationRepository,
                                   VerificationProperties properties) {
        this.verificationRepository = verificationRepository;
        this.properties = properties;
    }

    // run()에도 @Transactional을 단다 — 스케줄러는 프록시로 들어오지만 내부의 deleteExpired() 호출은
    // 자기 호출이라 프록시를 타지 않아서, 여기에 없으면 트랜잭션 없이 돈다.
    @Transactional
    @Scheduled(cron = "${coffeul.verification.cleanup-cron:0 0 4 * * *}", zone = "${coffeul.business-zone:Asia/Seoul}")
    public void run() {
        int deleted = deleteExpired(Instant.now());
        log.info("인증코드 정리 완료 — 삭제 {}건", deleted);
    }

    /**
     * 테스트에서 시각을 직접 넘길 수 있게 분리. 조회는 100건씩 끊지만 트랜잭션은 하나다 —
     * 순수 삭제라 "한 건 실패"라는 게 없고, 중간에 끊겨도 다음 실행이 같은 조건으로 이어 지운다.
     */
    @Transactional
    public int deleteExpired(Instant now) {
        Instant threshold = now.minus(properties.retention());
        int deleted = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<EmailVerification> expired = verificationRepository.findTop100ByExpiresAtLessThan(threshold);
            if (expired.isEmpty()) {
                return deleted;
            }
            verificationRepository.deleteAll(expired);
            verificationRepository.flush();
            deleted += expired.size();
        }
        log.warn("인증코드 정리가 한 번에 {}건 상한에 걸렸다 — 남은 행은 다음 실행에서 지운다", deleted);
        return deleted;
    }
}
