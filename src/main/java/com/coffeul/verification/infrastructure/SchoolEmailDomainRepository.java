package com.coffeul.verification.infrastructure;

import com.coffeul.verification.domain.SchoolEmailDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SchoolEmailDomainRepository extends JpaRepository<SchoolEmailDomain, Long> {

    Optional<SchoolEmailDomain> findByDomain(String domain);
}
