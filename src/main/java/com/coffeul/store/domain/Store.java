package com.coffeul.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** schema.sql `store` 테이블 매핑. */
@Entity
@Table(name = "store")
public class Store {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "location")
    private String location;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "notice", length = 500)
    private String notice;

    protected Store() {
    }

    public Long getId() {
        return id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public Long getSchoolId() {
        return schoolId;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public String getStatus() {
        return status;
    }

    public String getNotice() {
        return notice;
    }

    public boolean isOpen() {
        return "OPEN".equals(status);
    }

    /** MS-8: OPEN·PAUSED·CLOSED는 서로 자유롭게 오간다(단방향 전이 제약 없음) — 값 검증은 application 계층이 한다. */
    public void changeStatus(String status) {
        this.status = status;
    }
}
