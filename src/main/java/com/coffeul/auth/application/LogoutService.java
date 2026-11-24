package com.coffeul.auth.application;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.api.MemberCredentialPort;
import com.coffeul.auth.api.StaffCredentialPort;
import com.coffeul.auth.infrastructure.RefreshTokenRepository;
import com.coffeul.auth.infrastructure.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** MS-4(로그아웃). 이미 폐기됐거나 없는 refresh 토큰이 와도 그냥 200 — 멱등 (api.py memo). */
@Service
public class LogoutService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberCredentialPort members;
    private final StaffCredentialPort staff;

    public LogoutService(RefreshTokenRepository refreshTokenRepository, MemberCredentialPort members, StaffCredentialPort staff) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.members = members;
        this.staff = staff;
    }

    @Transactional
    public void logout(String refreshTokenPlain) {
        if (refreshTokenPlain == null || refreshTokenPlain.isBlank()) {
            return;
        }
        String hash = TokenHasher.sha256Hex(refreshTokenPlain);
        var subject = refreshTokenRepository.findSubjectByTokenHash(hash);
        if (subject.isEmpty()) return;
        // 갱신과 같은 계정 → 토큰 순서로 잠근다. 다른 기기의 별도 로그인 계열은 유지한다.
        if (AuthUser.SubjectType.MEMBER.name().equals(subject.get().getSubjectType())) {
            members.findByIdForUpdate(subject.get().getSubjectId());
        } else {
            staff.findByIdForUpdate(subject.get().getSubjectId());
        }
        refreshTokenRepository.findByTokenHashForUpdate(hash).ifPresent(token -> {
            Instant now = Instant.now();
            refreshTokenRepository.findUnexpiredFamilyForUpdate(token.getFamilyId(), now)
                    .forEach(related -> related.invalidate(now));
        });
    }
}
