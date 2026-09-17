package com.coffeul.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** schema.sql `order_line_option` 테이블 매핑. */
@Entity
@Table(name = "order_line_option")
public class OrderLineOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_line_id", nullable = false)
    private Long orderLineId;

    @Column(name = "option_item_id", nullable = false)
    private Long optionItemId;

    @Column(name = "option_group_name_snapshot", nullable = false, length = 30)
    private String optionGroupNameSnapshot;

    @Column(name = "option_item_name_snapshot", nullable = false, length = 30)
    private String optionItemNameSnapshot;

    @Column(name = "price_delta_snapshot", nullable = false)
    private int priceDeltaSnapshot;

    protected OrderLineOption() {
    }

    public static OrderLineOption of(Long orderLineId, Long optionItemId, String groupName,
                                      String itemName, int priceDelta) {
        OrderLineOption option = new OrderLineOption();
        option.orderLineId = orderLineId;
        option.optionItemId = optionItemId;
        option.optionGroupNameSnapshot = groupName;
        option.optionItemNameSnapshot = itemName;
        option.priceDeltaSnapshot = priceDelta;
        return option;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderLineId() {
        return orderLineId;
    }

    public String getOptionItemNameSnapshot() {
        return optionItemNameSnapshot;
    }
}
