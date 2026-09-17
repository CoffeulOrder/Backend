package com.coffeul.menu.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** schema.sql `menu_item` 테이블 매핑 (일부 컬럼만 — 기준 구현 범위). */
@Entity
@Table(name = "menu_item")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "base_price", nullable = false)
    private int basePrice;

    @Column(name = "image_key", length = 255)
    private String imageKey;

    @Column(name = "is_sold_out", nullable = false)
    private boolean soldOut;

    @Column(name = "status", nullable = false, length = 10)
    private String status = "ACTIVE";

    @Column(name = "is_new", nullable = false)
    private boolean isNew;

    @Column(name = "is_event", nullable = false)
    private boolean event;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected MenuItem() {
    }

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getBasePrice() {
        return basePrice;
    }

    public String getImageKey() {
        return imageKey;
    }

    public boolean isSoldOut() {
        return soldOut;
    }

    public String getStatus() {
        return status;
    }

    public boolean isNew() {
        return isNew;
    }

    public boolean isEvent() {
        return event;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
