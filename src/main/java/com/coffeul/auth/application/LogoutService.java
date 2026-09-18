package com.coffeul.auth.application;

import com.coffeul.auth.infrastructure.RefreshTokenRepository;
import com.coffeul.auth.infrastructure.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** MS-4(로그아웃). 이미 폐기됐거나 없는 refresh 토큰이 와도 그냥 200 — 멱등 (api.py memo). */
@Service
public class LogoutService {

    private final RefreshTokenRepository refreshTokenRepository;

    public LogoutService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public void logout(String refreshTokenPlain) {
        if (refreshTokenPlain == null || refreshTokenPlain.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(refreshTokenPlain))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.revoke(Instant.now()));
    }
}
