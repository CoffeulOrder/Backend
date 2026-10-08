package com.coffeul.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalTime;

/** schema.sql `store_business_hour` 테이블 매핑. 표시용 — 주문 가능 여부는 Store.status가 기준(REQ-ST-005). */
@Entity
@Table(name = "store_business_hour")
public class StoreBusinessHour {

    @EmbeddedId
    private StoreBusinessHourId id;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(name = "is_closed", nullable = false)
    private boolean closed;

    protected StoreBusinessHour() {
    }

    public Long getStoreId() {
        return id.getStoreId();
    }

    public int getDayOfWeek() {
        return id.getDayOfWeek();
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public boolean isClosed() {
        return closed;
    }
}
