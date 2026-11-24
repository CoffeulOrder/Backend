package com.coffeul.auth.infrastructure;

import com.coffeul.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    interface SubjectReference {
        String getSubjectType();
        Long getSubjectId();
    }

    /** 잠금 순서를 정할 불변 식별자만 읽어 엔티티의 오래된 상태를 캐시에 남기지 않는다. */
    @Query("select token.subjectType as subjectType, token.subjectId as subjectId "
            + "from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<SubjectReference> findSubjectByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.familyId = :familyId "
            + "and token.expiresAt > :now order by token.id")
    List<RefreshToken> findUnexpiredFamilyForUpdate(@Param("familyId") String familyId, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.subjectType = :subjectType and token.subjectId = :subjectId "
            + "and token.expiresAt > :now order by token.id")
    List<RefreshToken> findUnexpiredSubjectForUpdate(@Param("subjectType") String subjectType,
                                                  @Param("subjectId") Long subjectId, @Param("now") Instant now);

    List<RefreshToken> findByFamilyIdAndRevokedAtIsNull(String familyId);

    List<RefreshToken> findBySubjectTypeAndSubjectIdAndRevokedAtIsNull(String subjectType, Long subjectId);
}
