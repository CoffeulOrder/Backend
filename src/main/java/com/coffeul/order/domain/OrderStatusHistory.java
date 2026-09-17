package com.coffeul.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** schema.sql `order_status_history` 테이블 매핑. 상태 변경의 유일한 증거 — 상태를 바꿀 때마다 같은 트랜잭션에서 남긴다. */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(name = "actor_type", nullable = false, length = 10)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "reason", length = 200)
    private String reason;

    protected OrderStatusHistory() {
    }

    public static OrderStatusHistory created(Long orderId, String toStatus, Long customerMemberId) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.orderId = orderId;
        history.fromStatus = null;
        history.toStatus = toStatus;
        history.actorType = "CUSTOMER";
        history.actorId = customerMemberId;
        return history;
    }

    public Long getId() {
        return id;
    }
}
