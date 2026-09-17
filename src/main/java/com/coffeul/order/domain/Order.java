package com.coffeul.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** schema.sql `orders` 테이블 매핑 (기준 구현 범위 — 생성까지만, 상태 전이는 다음 단계). */
@Entity
@Table(name = "orders")
public class Order {

    public static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, length = 30)
    private String orderCode;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING_PAYMENT;

    @Column(name = "subtotal_amount", nullable = false)
    private int subtotalAmount;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Column(name = "request_memo", length = 100)
    private String requestMemo;

    @Column(name = "withdrawal_limit_agreed_at", nullable = false)
    private Instant withdrawalLimitAgreedAt;

    @Column(name = "commission_rate_snapshot", nullable = false)
    private BigDecimal commissionRateSnapshot;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected Order() {
    }

    public static Order create(String orderCode, Long memberId, Long storeId, Long schoolId,
                                int subtotalAmount, int discountAmount, String requestMemo,
                                Instant withdrawalLimitAgreedAt, BigDecimal commissionRateSnapshot,
                                String idempotencyKey, String requestHash, Instant expiresAt) {
        Order order = new Order();
        order.orderCode = orderCode;
        order.memberId = memberId;
        order.storeId = storeId;
        order.schoolId = schoolId;
        order.subtotalAmount = subtotalAmount;
        order.discountAmount = discountAmount;
        order.totalAmount = subtotalAmount - discountAmount;
        order.requestMemo = requestMemo;
        order.withdrawalLimitAgreedAt = withdrawalLimitAgreedAt;
        order.commissionRateSnapshot = commissionRateSnapshot;
        order.idempotencyKey = idempotencyKey;
        order.requestHash = requestHash;
        order.expiresAt = expiresAt;
        return order;
    }

    public Long getId() {
        return id;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getStatus() {
        return status;
    }

    public int getSubtotalAmount() {
        return subtotalAmount;
    }

    public int getDiscountAmount() {
        return discountAmount;
    }

    public int getTotalAmount() {
        return totalAmount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
