package com.coffeul.auth.application;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.api.StaffCredentialPort;
import com.coffeul.auth.api.StaffCredentialPort.StaffCredential;
import com.coffeul.auth.api.StaffCredentialPort.StaffStoreSummary;
import com.coffeul.auth.api.TokenIssuer;
import com.coffeul.common.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * MS-2(직원 로그인). 태블릿 상시 로그인이라 고객과 달리 <strong>계정을 잠그지 않는다</strong> —
 * 5회째부터 30초 대기만 건다 (태블릿이 잠기면 매장이 주문을 못 받아서, rules.py AUTH_SPEC).
 */
@Service
public class StaffLoginService {

    private static final String STAFF_LOCK_MESSAGE = "로그인 시도가 많아요. 30초 뒤에 다시 시도해주세요.";
    private static final String STAFF_INVALID_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";

    private final StaffCredentialPort staffCredentialPort;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final long lockSeconds;

    public StaffLoginService(StaffCredentialPort staffCredentialPort,
                              PasswordEncoder passwordEncoder,
                              TokenIssuer tokenIssuer,
                              @Value("${coffeul.auth.staff-login-lock-seconds:30}") long lockSeconds) {
        this.staffCredentialPort = staffCredentialPort;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.lockSeconds = lockSeconds;
    }

    public record Result(TokenIssuer.TokenPair tokens, Long staffId, String name, String role,
                          List<StaffStoreSummary> stores) {
    }

    // BusinessException은 실패 횟수를 이미 기록한 뒤에 던지는 정상 흐름이라 롤백 대상에서 뺀다 —
    // 안 그러면 30초 대기가 매번 롤백돼서 절대 작동하지 않는다.
    @Transactional(noRollbackFor = BusinessException.class)
    public Result login(String loginId, String password) {
        Instant now = Instant.now();
        StaffCredential credential = staffCredentialPort.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS, STAFF_INVALID_MESSAGE));

        if (credential.lockedUntil() != null && credential.lockedUntil().isAfter(now)) {
            throw BusinessException.withData(AuthErrorCode.LOCKED, STAFF_LOCK_MESSAGE,
                    Map.of("retryAfterSeconds", credential.lockedUntil().getEpochSecond() - now.getEpochSecond()));
        }

        if (!passwordEncoder.matches(password, credential.passwordHash())) {
            recordFailure(credential, now);
        }

        if (!credential.active()) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_UNUSABLE);
        }

        staffCredentialPort.recordLoginSuccess(credential.staffId(), now);
        AuthUser authUser = new AuthUser(credential.staffId(), AuthUser.SubjectType.STAFF,
                AuthUser.Role.valueOf(credential.role()), null, credential.merchantId());
        TokenIssuer.TokenPair tokens = tokenIssuer.issue(authUser);
        List<StaffStoreSummary> stores = staffCredentialPort.findAccessibleStores(credential.staffId());
        return new Result(tokens, credential.staffId(), credential.name(), credential.role(), stores);
    }

    private void recordFailure(StaffCredential credential, Instant now) {
        int newCount = credential.failedLoginCount() + 1;
        Instant lockedUntil = newCount >= 5 ? now.plusSeconds(lockSeconds) : null;
        staffCredentialPort.recordLoginFailure(credential.staffId(), now, lockedUntil);
        if (lockedUntil != null) {
            throw BusinessException.withData(AuthErrorCode.LOCKED, STAFF_LOCK_MESSAGE,
                    Map.of("retryAfterSeconds", lockSeconds));
        }
        throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS, STAFF_INVALID_MESSAGE);
    }
}
