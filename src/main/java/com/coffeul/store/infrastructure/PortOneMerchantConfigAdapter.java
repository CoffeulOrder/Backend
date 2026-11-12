package com.coffeul.store.infrastructure;

import com.coffeul.payment.api.PortOneMerchantConfigPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PortOneMerchantConfigAdapter implements PortOneMerchantConfigPort {
    private final JdbcTemplate jdbc;

    public PortOneMerchantConfigAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Config> findActiveInicisConfig(Long storeId) {
        return jdbc.query("""
                SELECT pg.id, pg.portone_store_id, pg.client_key FROM merchant_pg pg
                JOIN store s ON s.merchant_id = pg.merchant_id
                WHERE s.id = ? AND pg.pg_provider = 'INICIS_V2' AND pg.status = 'ACTIVE'
                """, (rs, row) -> new Config(rs.getLong("id"), rs.getString("portone_store_id"),
                rs.getString("client_key")), storeId).stream().findFirst();
    }
}
