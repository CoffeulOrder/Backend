package com.coffeul.store.infrastructure;

import com.coffeul.store.domain.StaffAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StaffAccountRepository extends JpaRepository<StaffAccount, Long> {

    Optional<StaffAccount> findByLoginId(String loginId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select staff from StaffAccount staff where staff.id = :id")
    Optional<StaffAccount> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select staff from StaffAccount staff where staff.loginId = :loginId")
    Optional<StaffAccount> findByLoginIdForUpdate(@Param("loginId") String loginId);
}
