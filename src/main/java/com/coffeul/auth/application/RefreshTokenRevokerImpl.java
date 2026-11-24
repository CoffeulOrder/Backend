package com.coffeul.auth.application;

import com.coffeul.auth.api.RefreshTokenRevoker;
import com.coffeul.auth.api.MemberCredentialPort;
import com.coffeul.auth.domain.RefreshToken;
import com.coffeul.auth.infrastructure.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** REQ-U-005 · 006의 "모든 refresh 토큰 폐기". */
@Service
public class RefreshTokenRevokerImpl implements RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberCredentialPort members;

    public RefreshTokenRevokerImpl(RefreshTokenRepository refreshTokenRepository, MemberCredentialPort members) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.members = members;
    }

    @Override
    @Transactional
    public int revokeAllForMember(Long memberId) {
        members.findByIdForUpdate(memberId);
        Instant now = Instant.now();
        List<RefreshToken> alive =
                refreshTokenRepository.findUnexpiredSubjectForUpdate("MEMBER", memberId, now);
        // 직전에 회전한 선행 토큰도 포함해야 30초 유예로 세션이 되살아나지 않는다.
        alive.forEach(token -> token.invalidate(now));
        return alive.size();
    }
}
