package com.coffeul.store.infrastructure;

import com.coffeul.store.domain.StaffAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffAccountRepository extends JpaRepository<StaffAccount, Long> {

    Optional<StaffAccount> findByLoginId(String loginId);
}
