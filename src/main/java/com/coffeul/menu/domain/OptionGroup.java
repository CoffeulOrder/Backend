package com.coffeul.menu.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** schema.sql `option_group` 테이블 매핑. */
@Entity
@Table(name = "option_group")
public class OptionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "option_type", nullable = false, length = 20)
    private String optionType;

    @Column(name = "is_required", nullable = false)
    private boolean required = true;

    @Column(name = "min_select", nullable = false)
    private byte minSelect = 1;

    @Column(name = "max_select", nullable = false)
    private byte maxSelect = 1;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected OptionGroup() {
    }

    public Long getId() {
        return id;
    }

    public Long getMenuItemId() {
        return menuItemId;
    }

    public String getName() {
        return name;
    }

    public String getOptionType() {
        return optionType;
    }

    public boolean isRequired() {
        return required;
    }

    public int getMinSelect() {
        return minSelect;
    }

    public int getMaxSelect() {
        return maxSelect;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
