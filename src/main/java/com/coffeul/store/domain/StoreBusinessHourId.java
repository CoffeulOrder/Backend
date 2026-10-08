package com.coffeul.store.domain;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** `store_business_hour`의 복합 PK (store_id, day_of_week). */
@Embeddable
public class StoreBusinessHourId implements Serializable {

    private Long storeId;
    private byte dayOfWeek;

    protected StoreBusinessHourId() {
    }

    public StoreBusinessHourId(Long storeId, int dayOfWeek) {
        this.storeId = storeId;
        this.dayOfWeek = (byte) dayOfWeek;
    }

    public Long getStoreId() {
        return storeId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StoreBusinessHourId other)) {
            return false;
        }
        return Objects.equals(storeId, other.storeId) && dayOfWeek == other.dayOfWeek;
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeId, dayOfWeek);
    }
}
