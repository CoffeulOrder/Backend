package com.coffeul.auth.application;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.api.MemberCredentialPort;
import com.coffeul.auth.api.StaffCredentialPort;
import com.coffeul.auth.api.TokenIssuer;
import com.coffeul.auth.domain.RefreshToken;
import com.coffeul.auth.infrastructure.RefreshTokenRepository;
import com.coffeul.auth.infrastructure.TokenHasher;
import com.coffeul.common.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * MS-3(토큰 재발급). rules.py AUTH_SPEC 재사용 감지: 이미 폐기된 refresh가 오면 탈취로 보고 같은
 * 계열 전부 폐기 — 단 폐기 후 30초 안이면 동시 요청으로 보고 새 쌍만 발급.
 */
@Service
public class TokenRefreshService {

    private static final long REUSE_GRACE_SECONDS = 30;

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenIssuer tokenIssuer;
    private final MemberCredentialPort memberCredentialPort;
    private final StaffCredentialPort staffCredentialPort;

    public TokenRefreshService(RefreshTokenRepository refreshTokenRepository,
                                TokenIssuer tokenIssuer,
                                MemberCredentialPort memberCredentialPort,
                                StaffCredentialPort staffCredentialPort) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenIssuer = tokenIssuer;
        this.memberCredentialPort = memberCredentialPort;
        this.staffCredentialPort = staffCredentialPort;
    }

    // 탈취 의심 시 계열 전체를 폐기한 뒤 BusinessException을 던지는 정상 흐름이 있다 — 롤백되면
    // 그 폐기가 사라져서 탈취된 토큰이 계속 살아있게 된다.
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenIssuer.TokenPair refresh(String refreshTokenPlain) {
        String hash = TokenHasher.sha256Hex(refreshTokenPlain);
        var subject = refreshTokenRepository.findSubjectByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_INVALID));
        AuthUser user = rebuildAuthUser(subject);
        RefreshToken existing = refreshTokenRepository.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_INVALID));

        Instant now = Instant.now();
        if (!existing.getExpiresAt().isAfter(now)) {
            throw new BusinessException(AuthErrorCode.REFRESH_INVALID);
        }

        if (existing.getRevokedAt() != null) {
            if (existing.getRevokedAt().isAfter(now.minusSeconds(REUSE_GRACE_SECONDS))) {
                return tokenIssuer.rotate(user, existing.getFamilyId());
            }
            revokeFamily(existing.getFamilyId(), now);
            throw new BusinessException(AuthErrorCode.REFRESH_INVALID);
        }

        existing.revoke(now);
        return tokenIssuer.rotate(user, existing.getFamilyId());
    }

    private void revokeFamily(String familyId, Instant now) {
        refreshTokenRepository.findUnexpiredFamilyForUpdate(familyId, now)
                .forEach(token -> token.invalidate(now));
    }

    private AuthUser rebuildAuthUser(RefreshTokenRepository.SubjectReference subject) {
        if (AuthUser.SubjectType.MEMBER.name().equals(subject.getSubjectType())) {
            var credential = memberCredentialPort.findByIdForUpdate(subject.getSubjectId())
                    .filter(MemberCredentialPort.MemberCredential::active)
                    .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_INVALID));
            return new AuthUser(credential.memberId(), AuthUser.SubjectType.MEMBER,
                    AuthUser.Role.CUSTOMER, credential.schoolId(), null);
        }
        var credential = staffCredentialPort.findByIdForUpdate(subject.getSubjectId())
                .filter(StaffCredentialPort.StaffCredential::active)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_INVALID));
        return new AuthUser(credential.staffId(), AuthUser.SubjectType.STAFF,
                AuthUser.Role.valueOf(credential.role()), null, credential.merchantId());
    }
}
