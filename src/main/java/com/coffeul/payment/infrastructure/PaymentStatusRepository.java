package com.coffeul.payment.infrastructure;

import com.coffeul.payment.api.PaymentStatusApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class PaymentStatusRepository implements PaymentStatusApi {
    private final JdbcTemplate jdbc;

    public PaymentStatusRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Snapshot> findLatest(long orderId) {
        return jdbc.query("""
                SELECT p.attempt_no, p.pg_provider, p.pg_order_id, p.status, p.amount, p.method,
                    p.approved_at, p.canceled_at, c.id AS cancel_id, c.status AS refund_status,
                    c.cancel_amount, c.succeeded_at FROM payment p
                LEFT JOIN payment_cancel c ON c.id = (SELECT r.id FROM payment_cancel r
                    WHERE r.payment_id = p.id ORDER BY r.id DESC LIMIT 1)
                WHERE p.order_id = ? ORDER BY p.attempt_no DESC LIMIT 1
                """, (rs, row) -> new Snapshot(rs.getInt("attempt_no"), rs.getString("pg_provider"),
                rs.getString("pg_order_id"), rs.getString("status"), rs.getInt("amount"), rs.getString("method"),
                instant(rs, "approved_at"), instant(rs, "canceled_at"), rs.getObject("cancel_id") == null ? null
                : new Refund(rs.getString("refund_status"), rs.getInt("cancel_amount"), instant(rs, "succeeded_at"))),
                orderId).stream().findFirst();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime time = rs.getObject(column, LocalDateTime.class);
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
}
