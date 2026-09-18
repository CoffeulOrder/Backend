package com.coffeul.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** schema.sql `order_line` 테이블 매핑. 메뉴명 · 가격은 주문 시점 스냅샷. */
@Entity
@Table(name = "order_line")
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @Column(name = "menu_name_snapshot", nullable = false, length = 50)
    private String menuNameSnapshot;

    @Column(name = "base_price_snapshot", nullable = false)
    private int basePriceSnapshot;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    @Column(name = "quantity", nullable = false)
    private short quantity;

    @Column(name = "line_amount", nullable = false)
    private int lineAmount;

    protected OrderLine() {
    }

    public static OrderLine of(Long orderId, Long menuItemId, String menuNameSnapshot,
                                int basePriceSnapshot, int unitPrice, int quantity) {
        OrderLine line = new OrderLine();
        line.orderId = orderId;
        line.menuItemId = menuItemId;
        line.menuNameSnapshot = menuNameSnapshot;
        line.basePriceSnapshot = basePriceSnapshot;
        line.unitPrice = unitPrice;
        line.quantity = (short) quantity;
        line.lineAmount = unitPrice * quantity;
        return line;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getMenuItemId() {
        return menuItemId;
    }

    public String getMenuNameSnapshot() {
        return menuNameSnapshot;
    }

    public int getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getLineAmount() {
        return lineAmount;
    }
}
