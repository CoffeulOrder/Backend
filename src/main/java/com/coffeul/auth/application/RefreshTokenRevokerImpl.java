package com.coffeul.auth.application;

import com.coffeul.auth.api.RefreshTokenRevoker;
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

    public RefreshTokenRevokerImpl(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    @Transactional
    public int revokeAllForMember(Long memberId) {
        Instant now = Instant.now();
        List<RefreshToken> alive =
                refreshTokenRepository.findBySubjectTypeAndSubjectIdAndRevokedAtIsNull("MEMBER", memberId);
        // 정상 회전이 아니라 강제 폐기다 — 30초 유예 창으로 되살아나지 않게 만료 시각도 같이 당긴다.
        alive.forEach(token -> token.revokeDueToReuse(now));
        return alive.size();
    }
}
