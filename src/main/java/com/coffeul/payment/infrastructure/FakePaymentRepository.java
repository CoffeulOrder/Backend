package com.coffeul.payment.infrastructure;

import com.coffeul.payment.api.FakePaymentApi;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FakePaymentRepository {
    private final JdbcTemplate jdbc;
    public FakePaymentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void prepare(Long orderId, Long storeId, Long merchantPgId, String pgOrderId, int amount) {
        jdbc.update("INSERT INTO payment (order_id,store_id,merchant_pg_id,pg_provider,attempt_no,pg_order_id,"
                        + "amount,status,confirm_idempotency_key) VALUES (?,?,?,'FAKE',1,?,?,'READY',?) "
                        + "ON DUPLICATE KEY UPDATE id = id",
                orderId, storeId, merchantPgId, pgOrderId, amount, UUID.randomUUID().toString());
    }

    public Optional<FakePaymentApi.State> findForUpdate(Long orderId, String pgOrderId) {
        return jdbc.query("SELECT status,amount,approved_at FROM payment "
                        + "WHERE order_id = ? AND pg_order_id = ? AND pg_provider = 'FAKE' FOR UPDATE",
                (rs, row) -> {
                    var approvedAt = rs.getTimestamp("approved_at");
                    return new FakePaymentApi.State(rs.getString("status"), rs.getInt("amount"),
                            approvedAt == null ? null : approvedAt.toLocalDateTime().toInstant(ZoneOffset.UTC));
                }, orderId, pgOrderId).stream().findFirst();
    }

    public void approve(Long orderId, String pgOrderId, Instant now) {
        jdbc.update("UPDATE payment SET status = 'APPROVED', payment_key = ?, method = 'FAKE', approved_at = ? "
                        + "WHERE order_id = ? AND pg_order_id = ? AND pg_provider = 'FAKE' AND status = 'READY'",
                "FAKE-" + pgOrderId, now, orderId, pgOrderId);
    }

    public void expire(Long orderId, Instant now) {
        jdbc.update("UPDATE payment SET status = 'FAILED', failed_at = ?, failure_code = 'ORDER_EXPIRED' "
                + "WHERE order_id = ? AND pg_provider = 'FAKE' AND status = 'READY'", now, orderId);
    }
}
