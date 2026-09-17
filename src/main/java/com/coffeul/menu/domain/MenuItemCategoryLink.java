package com.coffeul.menu.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** schema.sql `menu_item_category` 다대다 조인 테이블 매핑. */
@Entity
@Table(name = "menu_item_category")
public class MenuItemCategoryLink {

    @EmbeddedId
    private Id id;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected MenuItemCategoryLink() {
    }

    public MenuItemCategoryLink(Long menuItemId, Long categoryId, int sortOrder) {
        this.id = new Id(menuItemId, categoryId);
        this.sortOrder = sortOrder;
    }

    public Long getMenuItemId() {
        return id.menuItemId;
    }

    public Long getCategoryId() {
        return id.categoryId;
    }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "menu_item_id")
        private Long menuItemId;
        @Column(name = "category_id")
        private Long categoryId;

        protected Id() {
        }

        public Id(Long menuItemId, Long categoryId) {
            this.menuItemId = menuItemId;
            this.categoryId = categoryId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return Objects.equals(menuItemId, id.menuItemId) && Objects.equals(categoryId, id.categoryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(menuItemId, categoryId);
        }
    }
}
