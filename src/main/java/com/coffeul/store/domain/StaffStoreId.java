package com.coffeul.store.domain;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** `staff_store`의 복합 PK (staff_account_id, store_id). */
@Embeddable
public class StaffStoreId implements Serializable {

    private Long staffAccountId;
    private Long storeId;

    protected StaffStoreId() {
    }

    public StaffStoreId(Long staffAccountId, Long storeId) {
        this.staffAccountId = staffAccountId;
        this.storeId = storeId;
    }

    public Long getStaffAccountId() {
        return staffAccountId;
    }

    public Long getStoreId() {
        return storeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StaffStoreId other)) {
            return false;
        }
        return Objects.equals(staffAccountId, other.staffAccountId) && Objects.equals(storeId, other.storeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(staffAccountId, storeId);
    }
}
