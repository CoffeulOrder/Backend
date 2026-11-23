package com.coffeul.payment.infrastructure;

import com.coffeul.payment.api.PaymentRecoveryApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class PaymentRecoveryRepository implements PaymentRecoveryApi {
    private final JdbcTemplate jdbc;

    public PaymentRecoveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void discover(Instant now, int limit) {
        jdbc.update("""
                INSERT INTO payment_reconciliation (payment_id, next_retry_at)
                SELECT p.id, ? FROM payment p JOIN orders o ON o.id = p.order_id
                LEFT JOIN payment_reconciliation r ON r.payment_id = p.id
                LEFT JOIN payment_cancel c ON c.payment_id = p.id
                WHERE p.pg_provider = 'INICIS_V2' AND p.status IN ('READY', 'UNKNOWN', 'APPROVED')
                    AND (o.status = 'EXPIRED' OR (o.status = 'PENDING_PAYMENT' AND o.expires_at <= ?))
                    AND r.payment_id IS NULL AND c.id IS NULL
                ORDER BY p.id LIMIT ?
                ON DUPLICATE KEY UPDATE payment_id = payment_reconciliation.payment_id
                """, utc(now), utc(now), limit);
    }

    @Override
    public List<Long> dueIds(Instant now, int limit) {
        return jdbc.query("""
                SELECT payment_id FROM payment_reconciliation
                WHERE status = 'WAITING' AND next_retry_at <= ? ORDER BY next_retry_at, payment_id LIMIT ?
                """, (rs, row) -> rs.getLong(1), utc(now), limit);
    }

    @Override
    public Optional<Claim> claim(long paymentId, Instant now, Instant leaseUntil) {
        if (jdbc.update("""
                UPDATE payment_reconciliation SET attempt_count = attempt_count + 1, next_retry_at = ?
                WHERE payment_id = ? AND status = 'WAITING' AND next_retry_at <= ?
                """, utc(leaseUntil), paymentId, utc(now)) == 0) {
            return Optional.empty();
        }
        return jdbc.query("""
                SELECT r.payment_id, p.order_id, p.pg_order_id, r.attempt_count
                FROM payment_reconciliation r JOIN payment p ON p.id = r.payment_id WHERE r.payment_id = ?
                """, (rs, row) -> new Claim(rs.getLong("payment_id"), rs.getLong("order_id"),
                rs.getString("pg_order_id"), rs.getInt("attempt_count")), paymentId).stream().findFirst();
    }

    @Override
    public void complete(Claim claim) {
        jdbc.update("""
                UPDATE payment_reconciliation SET status = 'COMPLETE', last_error_code = NULL
                WHERE payment_id = ? AND status = 'WAITING' AND attempt_count = ?
                """, claim.paymentId(), claim.attempt());
    }

    @Override
    public void retry(Claim claim, Instant retryAt, String safeCode) {
        jdbc.update("""
                UPDATE payment_reconciliation SET next_retry_at = ?, last_error_code = ?
                WHERE payment_id = ? AND status = 'WAITING' AND attempt_count = ?
                """, utc(retryAt), safeCode, claim.paymentId(), claim.attempt());
    }

    @Override
    public void fail(Claim claim, String safeCode) {
        jdbc.update("""
                UPDATE payment_reconciliation SET status = 'FAILED', last_error_code = ?
                WHERE payment_id = ? AND status = 'WAITING' AND attempt_count = ?
                """, safeCode, claim.paymentId(), claim.attempt());
    }

    private static LocalDateTime utc(Instant time) {
        return LocalDateTime.ofInstant(time, ZoneOffset.UTC);
    }
}
