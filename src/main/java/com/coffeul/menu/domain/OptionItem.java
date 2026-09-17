package com.coffeul.menu.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** schema.sql `option_item` 테이블 매핑. */
@Entity
@Table(name = "option_item")
public class OptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "option_group_id", nullable = false)
    private Long optionGroupId;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "price_delta", nullable = false)
    private int priceDelta;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "is_sold_out", nullable = false)
    private boolean soldOut;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected OptionItem() {
    }

    public Long getId() {
        return id;
    }

    public Long getOptionGroupId() {
        return optionGroupId;
    }

    public String getName() {
        return name;
    }

    public int getPriceDelta() {
        return priceDelta;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public boolean isSoldOut() {
        return soldOut;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
