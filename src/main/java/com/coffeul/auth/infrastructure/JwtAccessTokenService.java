package com.coffeul.auth.infrastructure;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * access 토큰 발급 · 검증 (rules.py AUTH_SPEC: JWT · HS256 · 유효 1시간).
 * 클레임: sub · typ(MEMBER|STAFF) · role · sch(고객만) · mer(OWNER·STAFF만) · iat · exp.
 */
@Component
public class JwtAccessTokenService {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_SCHOOL = "sch";
    private static final String CLAIM_MERCHANT = "mer";

    private final SecretKey key;
    private final Duration ttl;

    public JwtAccessTokenService(@Value("${coffeul.auth.jwt-secret}") String secret,
                                  @Value("${coffeul.auth.access-token-expire-minutes:60}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    public String encode(AuthUser user, Instant now) {
        var builder = Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim(CLAIM_TYPE, user.type().name())
                .claim(CLAIM_ROLE, user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        if (user.schoolId() != null) {
            builder.claim(CLAIM_SCHOOL, user.schoolId());
        }
        if (user.merchantId() != null) {
            builder.claim(CLAIM_MERCHANT, user.merchantId());
        }
        return builder.signWith(key).compact();
    }

    /** Authorization 헤더의 access 토큰을 검증하고 AuthUser로 되돌린다. 무효 · 만료면 C002(401). */
    public AuthUser decode(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Long id = Long.valueOf(claims.getSubject());
            AuthUser.SubjectType type = AuthUser.SubjectType.valueOf(claims.get(CLAIM_TYPE, String.class));
            AuthUser.Role role = AuthUser.Role.valueOf(claims.get(CLAIM_ROLE, String.class));
            Long schoolId = claims.get(CLAIM_SCHOOL, Long.class);
            Long merchantId = claims.get(CLAIM_MERCHANT, Long.class);
            return new AuthUser(id, type, role, schoolId, merchantId);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
