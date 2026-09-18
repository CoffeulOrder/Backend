package com.coffeul.auth.application;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.api.MemberCredentialPort;
import com.coffeul.auth.api.MemberCredentialPort.MemberCredential;
import com.coffeul.auth.api.TokenIssuer;
import com.coffeul.common.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** MS-1(고객 로그인). */
@Service
public class MemberLoginService {

    private final MemberCredentialPort memberCredentialPort;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final long lockMinutes;

    public MemberLoginService(MemberCredentialPort memberCredentialPort,
                               PasswordEncoder passwordEncoder,
                               TokenIssuer tokenIssuer,
                               @Value("${coffeul.auth.member-login-lock-minutes:15}") long lockMinutes) {
        this.memberCredentialPort = memberCredentialPort;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.lockMinutes = lockMinutes;
    }

    public record Result(TokenIssuer.TokenPair tokens, Long memberId, String name, Long schoolId) {
    }

    // BusinessException은 실패 횟수를 이미 기록한 뒤에 던지는 정상 흐름이라 롤백 대상에서 뺀다 —
    // 안 그러면 5회 실패 잠금이 매번 롤백돼서 절대 작동하지 않는다.
    @Transactional(noRollbackFor = BusinessException.class)
    public Result login(String email, String password) {
        Instant now = Instant.now();
        MemberCredential credential = memberCredentialPort.findByEmail(email)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if (credential.lockedUntil() != null && credential.lockedUntil().isAfter(now)) {
            throw BusinessException.withData(AuthErrorCode.LOCKED, AuthErrorCode.LOCKED.message(),
                    Map.of("lockedUntil", credential.lockedUntil().toString()));
        }

        if (!passwordEncoder.matches(password, credential.passwordHash())) {
            recordFailure(credential, now);
        }

        if (!credential.active()) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_UNUSABLE);
        }

        memberCredentialPort.recordLoginSuccess(credential.memberId(), now);
        AuthUser authUser = new AuthUser(credential.memberId(), AuthUser.SubjectType.MEMBER,
                AuthUser.Role.CUSTOMER, credential.schoolId(), null);
        TokenIssuer.TokenPair tokens = tokenIssuer.issue(authUser);
        return new Result(tokens, credential.memberId(), credential.name(), credential.schoolId());
    }

    private void recordFailure(MemberCredential credential, Instant now) {
        int newCount = credential.failedLoginCount() + 1;
        Instant lockedUntil = newCount >= 5 ? now.plus(lockMinutes, ChronoUnit.MINUTES) : null;
        memberCredentialPort.recordLoginFailure(credential.memberId(), now, lockedUntil);
        if (lockedUntil != null) {
            throw BusinessException.withData(AuthErrorCode.LOCKED, AuthErrorCode.LOCKED.message(),
                    Map.of("lockedUntil", lockedUntil.toString()));
        }
        throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }
}
