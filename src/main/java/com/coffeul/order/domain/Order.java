package com.coffeul.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** schema.sql `orders` 테이블 매핑. 상태 전이(MS-22~26·만료 작업)는 {@link #accept}류 메서드에만 둔다 — rules.py ORDER_RULES. */
@Entity
@Table(name = "orders")
public class Order {

    public static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_MAKING = "MAKING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_EXPIRED = "EXPIRED";

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

    @Column(name = "request_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String requestHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "pickup_no")
    private Short pickupNo;

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "making_at")
    private Instant makingAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

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

    public Long getStoreId() {
        return storeId;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public Short getPickupNo() {
        return pickupNo;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public String getRequestMemo() {
        return requestMemo;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getMakingAt() {
        return makingAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
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

    /** MS-22 수락: REQUESTED → ACCEPTED. */
    public void accept(Instant now) {
        requireStatus(STATUS_REQUESTED);
        status = STATUS_ACCEPTED;
        acceptedAt = now;
    }

    /** MS-24 제조 시작: ACCEPTED → MAKING. */
    public void startMaking(Instant now) {
        requireStatus(STATUS_ACCEPTED);
        status = STATUS_MAKING;
        makingAt = now;
    }

    /** MS-25 픽업 대기: MAKING → READY. */
    public void markReady(Instant now) {
        requireStatus(STATUS_MAKING);
        status = STATUS_READY;
        readyAt = now;
    }

    /** MS-26 픽업 완료: READY → COMPLETED. */
    public void complete(Instant now) {
        requireStatus(STATUS_READY);
        status = STATUS_COMPLETED;
        completedAt = now;
    }

    /** 결제 대기 만료 작업: PENDING_PAYMENT → EXPIRED. */
    public void expire(Instant now) {
        requireStatus(STATUS_PENDING_PAYMENT);
        status = STATUS_EXPIRED;
        expiredAt = now;
    }

    private void requireStatus(String expected) {
        if (!expected.equals(status)) {
            throw new InvalidOrderTransitionException(status);
        }
    }
}
