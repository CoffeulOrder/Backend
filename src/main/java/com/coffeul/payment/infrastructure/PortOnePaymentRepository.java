package com.coffeul.payment.infrastructure;

import com.coffeul.payment.api.PortOneMerchantConfigPort;
import com.coffeul.payment.api.PortOnePaymentApi.Attempt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PortOnePaymentRepository {
    public static final String PROVIDER = "INICIS_V2";
    private static final String SELECT_ATTEMPT = """
            SELECT id, order_id, pg_order_id, status, amount, portone_store_id, portone_channel_key, method, approved_at
            FROM payment WHERE pg_order_id = ? AND pg_provider = ?
            """;
    private static final RowMapper<Attempt> ATTEMPT_ROW = (rs, row) -> {
        var approvedAt = rs.getObject("approved_at", LocalDateTime.class);
        return new Attempt(rs.getLong("id"), rs.getLong("order_id"), rs.getString("pg_order_id"),
                rs.getString("status"), rs.getInt("amount"), rs.getString("portone_store_id"),
                rs.getString("portone_channel_key"), rs.getString("method"),
                approvedAt == null ? null : approvedAt.toInstant(ZoneOffset.UTC));
    };

    private final JdbcTemplate jdbc;

    public PortOnePaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void prepare(Long orderId, Long storeId, PortOneMerchantConfigPort.Config config,
                        String paymentId, int amount) {
        jdbc.update("""
                INSERT INTO payment (order_id, store_id, merchant_pg_id, pg_provider, attempt_no,
                    pg_order_id, amount, status, confirm_idempotency_key, portone_store_id, portone_channel_key)
                VALUES (?, ?, ?, ?, 1, ?, ?, 'READY', ?, ?, ?)
                """, orderId, storeId, config.merchantPgId(), PROVIDER, paymentId, amount,
                UUID.randomUUID().toString(), config.portOneStoreId(), config.channelKey());
    }

    public Optional<Attempt> find(Long orderId, String paymentId) {
        return find(orderId, paymentId, false);
    }

    public Optional<Attempt> findByPaymentId(String paymentId) {
        return jdbc.query(SELECT_ATTEMPT, ATTEMPT_ROW, paymentId, PROVIDER).stream()
                .filter(attempt -> paymentId.equals(attempt.paymentId())).findFirst();
    }

    public Optional<Attempt> findForUpdate(Long orderId, String paymentId) {
        return find(orderId, paymentId, true);
    }

    private Optional<Attempt> find(Long orderId, String paymentId, boolean lock) {
        String sql = SELECT_ATTEMPT + "AND order_id = ?" + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, ATTEMPT_ROW, paymentId, PROVIDER, orderId).stream()
                .filter(attempt -> paymentId.equals(attempt.paymentId())).findFirst();
    }

    public int approve(long id, String transactionId, String method, Instant paidAt) {
        return jdbc.update("""
                UPDATE payment SET status = 'APPROVED', payment_key = ?, method = ?, approved_at = ?
                WHERE id = ? AND status IN ('READY', 'UNKNOWN')
                """, transactionId, method, LocalDateTime.ofInstant(paidAt, ZoneOffset.UTC), id);
    }
}
