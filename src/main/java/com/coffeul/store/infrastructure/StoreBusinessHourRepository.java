package com.coffeul.store.infrastructure;

import com.coffeul.store.domain.StoreBusinessHour;
import com.coffeul.store.domain.StoreBusinessHourId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreBusinessHourRepository extends JpaRepository<StoreBusinessHour, StoreBusinessHourId> {

    List<StoreBusinessHour> findByIdStoreIdOrderByIdDayOfWeek(Long storeId);
}
