package com.coffeul.payment.infrastructure;

import com.coffeul.payment.api.PaymentCancellationApi.Cancellation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentCancellationRepository {
    public record Payment(long id, long orderId, String provider, String paymentId, String transactionId,
                          String storeId, String channelKey, int amount, String status) { }
    public record CancellationRequest(Cancellation cancellation, String requestedByType, String reason) { }
    public record Claim(long id, Payment payment, int amount, String idempotencyKey, int attempt,
                        String requestedByType, String reason) { }

    private final JdbcTemplate jdbc;

    public PaymentCancellationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Payment> lockPayment(long orderId) {
        return jdbc.query("""
                SELECT * FROM payment WHERE order_id = ? AND status IN ('APPROVED', 'CANCELED') FOR UPDATE
                """, (rs, row) -> new Payment(rs.getLong("id"), rs.getLong("order_id"), rs.getString("pg_provider"),
                rs.getString("pg_order_id"), rs.getString("payment_key"), rs.getString("portone_store_id"),
                rs.getString("portone_channel_key"), rs.getInt("amount"), rs.getString("status")), orderId)
                .stream().findFirst();
    }

    public Optional<Cancellation> findByOrderId(long orderId) {
        return findRequestByOrderId(orderId).map(CancellationRequest::cancellation);
    }

    public Optional<CancellationRequest> findRequestByOrderId(long orderId) {
        return jdbc.query("""
                SELECT c.id, c.status, c.cancel_amount, c.requested_by_type, c.cancel_reason FROM payment_cancel c
                JOIN payment p ON p.id = c.payment_id WHERE p.order_id = ? ORDER BY c.id LIMIT 1
                """, (rs, row) -> new CancellationRequest(new Cancellation(rs.getLong("id"), rs.getString("status"),
                rs.getInt("cancel_amount")), rs.getString("requested_by_type"), rs.getString("cancel_reason")),
                orderId).stream().findFirst();
    }

    public Cancellation insert(Payment payment, String actor, String reason) {
        jdbc.update("""
                INSERT INTO payment_cancel (payment_id, cancel_amount, cancel_reason, requested_by_type,
                    status, idempotency_key) VALUES (?, ?, ?, ?, 'REQUESTED', ?)
                """, payment.id(), payment.amount(), reason, actor, UUID.randomUUID().toString());
        return findByOrderId(payment.orderId()).orElseThrow();
    }

    public List<Long> dueIds(String provider, Instant now, int limit) {
        return jdbc.query("""
                SELECT c.id FROM payment_cancel c JOIN payment p ON p.id = c.payment_id
                JOIN orders o ON o.id = p.order_id
                WHERE c.status = 'REQUESTED' AND (c.next_retry_at IS NULL OR c.next_retry_at <= ?)
                    AND p.pg_provider = ? AND p.status IN ('APPROVED', 'CANCELED')
                    AND ((o.status = 'CANCELED' AND c.requested_by_type = 'CUSTOMER')
                        OR (o.status = 'REJECTED' AND c.requested_by_type = 'STAFF')
                        OR (o.status IN ('EXPIRED','REJECTED') AND c.requested_by_type = 'SYSTEM'))
                ORDER BY c.id LIMIT ?
                """, (rs, row) -> rs.getLong(1), utc(now), provider, limit);
    }

    public Optional<Claim> claim(long id, String provider, Instant now, Instant leaseUntil) {
        int updated = jdbc.update("""
                UPDATE payment_cancel c JOIN payment p ON p.id = c.payment_id
                JOIN orders o ON o.id = p.order_id
                SET c.attempt_count = c.attempt_count + 1, c.next_retry_at = ?
                WHERE c.id = ? AND c.status = 'REQUESTED'
                    AND (c.next_retry_at IS NULL OR c.next_retry_at <= ?)
                    AND p.pg_provider = ? AND p.status IN ('APPROVED', 'CANCELED')
                    AND ((o.status = 'CANCELED' AND c.requested_by_type = 'CUSTOMER')
                        OR (o.status = 'REJECTED' AND c.requested_by_type = 'STAFF')
                        OR (o.status IN ('EXPIRED','REJECTED') AND c.requested_by_type = 'SYSTEM'))
                """, utc(leaseUntil), id, utc(now), provider);
        if (updated == 0) {
            return Optional.empty();
        }
        return jdbc.query("""
                SELECT c.id AS cancel_id, c.cancel_amount, c.idempotency_key, c.attempt_count,
                    c.requested_by_type, c.cancel_reason, p.*
                FROM payment_cancel c JOIN payment p ON p.id = c.payment_id WHERE c.id = ?
                """, (rs, row) -> new Claim(rs.getLong("cancel_id"), new Payment(rs.getLong("id"),
                rs.getLong("order_id"), rs.getString("pg_provider"), rs.getString("pg_order_id"),
                rs.getString("payment_key"), rs.getString("portone_store_id"), rs.getString("portone_channel_key"),
                rs.getInt("amount"), rs.getString("status")), rs.getInt("cancel_amount"),
                rs.getString("idempotency_key"), rs.getInt("attempt_count"), rs.getString("requested_by_type"),
                rs.getString("cancel_reason")), id).stream().findFirst();
    }

    public void succeed(Claim claim, Instant now) {
        // Request transactions lock payment before cancellation. Keep the same order here.
        Payment payment = lockPayment(claim.payment().orderId()).orElseThrow();
        if (payment.id() != claim.payment().id()) {
            throw new IllegalStateException("CANCEL_PAYMENT_CHANGED");
        }
        int updated = jdbc.update("""
                UPDATE payment_cancel SET status = 'SUCCEEDED', succeeded_at = ?, next_retry_at = NULL,
                    last_error_code = NULL, last_error_message = NULL
                WHERE id = ? AND status = 'REQUESTED' AND attempt_count = ?
                """, utc(now), claim.id(), claim.attempt());
        if (updated == 1 && "APPROVED".equals(payment.status())) {
            if (jdbc.update("""
                    UPDATE payment SET status = 'CANCELED', canceled_at = ?, version = version + 1
                    WHERE id = ? AND status = 'APPROVED'
                    """, utc(now), payment.id()) != 1) {
                throw new IllegalStateException("CANCEL_PAYMENT_CHANGED");
            }
        }
    }

    public void retry(Claim claim, Instant retryAt, String safeCode) {
        jdbc.update("""
                UPDATE payment_cancel SET next_retry_at = ?, last_error_code = ?, last_error_message = NULL
                WHERE id = ? AND status = 'REQUESTED' AND attempt_count = ?
                """, utc(retryAt), safeCode, claim.id(), claim.attempt());
    }

    public void fail(Claim claim, String safeCode) {
        jdbc.update("""
                UPDATE payment_cancel SET status = 'FAILED', next_retry_at = NULL,
                    last_error_code = ?, last_error_message = NULL
                WHERE id = ? AND status = 'REQUESTED' AND attempt_count = ?
                """, safeCode, claim.id(), claim.attempt());
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
