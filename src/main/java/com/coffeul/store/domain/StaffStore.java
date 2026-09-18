package com.coffeul.store.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** schema.sql `staff_store` 테이블 매핑. STAFF가 접근 가능한 매장 (OWNER는 이 행 없이 merchant 소유 매장 전체 접근). */
@Entity
@Table(name = "staff_store")
public class StaffStore {

    @EmbeddedId
    private StaffStoreId id;

    protected StaffStore() {
    }

    public StaffStore(Long staffAccountId, Long storeId) {
        this.id = new StaffStoreId(staffAccountId, storeId);
    }

    public Long getStaffAccountId() {
        return id.getStaffAccountId();
    }

    public Long getStoreId() {
        return id.getStoreId();
    }
}
