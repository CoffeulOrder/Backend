package com.coffeul.store.infrastructure;

import com.coffeul.store.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByMerchantId(Long merchantId);

    List<Store> findBySchoolId(Long schoolId);
}
