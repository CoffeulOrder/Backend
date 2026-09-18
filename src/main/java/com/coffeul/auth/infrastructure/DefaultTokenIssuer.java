package com.coffeul.auth.infrastructure;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.api.TokenIssuer;
import com.coffeul.auth.domain.RefreshToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class DefaultTokenIssuer implements TokenIssuer {

    private final JwtAccessTokenService jwtAccessTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final long memberRefreshDays;
    private final long staffRefreshDays;

    public DefaultTokenIssuer(JwtAccessTokenService jwtAccessTokenService,
                               RefreshTokenRepository refreshTokenRepository,
                               @Value("${coffeul.auth.member-refresh-token-expire-days:14}") long memberRefreshDays,
                               @Value("${coffeul.auth.staff-refresh-token-expire-days:30}") long staffRefreshDays) {
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.memberRefreshDays = memberRefreshDays;
        this.staffRefreshDays = staffRefreshDays;
    }

    @Override
    @Transactional
    public TokenPair issue(AuthUser user) {
        return issueInternal(user, UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public TokenPair rotate(AuthUser user, String familyId) {
        return issueInternal(user, familyId);
    }

    private TokenPair issueInternal(AuthUser user, String familyId) {
        Instant now = Instant.now();
        String accessToken = jwtAccessTokenService.encode(user, now);
        String refreshTokenPlain = RefreshTokenGenerator.generate();
        long days = user.type() == AuthUser.SubjectType.MEMBER ? memberRefreshDays : staffRefreshDays;

        RefreshToken token = RefreshToken.issue(user.type().name(), user.id(),
                TokenHasher.sha256Hex(refreshTokenPlain), familyId, now.plus(days, ChronoUnit.DAYS));
        refreshTokenRepository.save(token);

        return new TokenPair(accessToken, refreshTokenPlain, jwtAccessTokenService.ttlSeconds());
    }
}
