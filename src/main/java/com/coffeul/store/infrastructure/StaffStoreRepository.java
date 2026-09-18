package com.coffeul.store.infrastructure;

import com.coffeul.store.domain.StaffStore;
import com.coffeul.store.domain.StaffStoreId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffStoreRepository extends JpaRepository<StaffStore, StaffStoreId> {

    List<StaffStore> findByIdStaffAccountId(Long staffAccountId);
}
