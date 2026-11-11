package com.coffeul.store.infrastructure;

import com.coffeul.payment.api.FakeMerchantConfigPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class FakeMerchantConfigAdapter implements FakeMerchantConfigPort {
    private final JdbcTemplate jdbc;
    public FakeMerchantConfigAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Config> findActiveFakeConfig(Long storeId) {
        return jdbc.query("SELECT pg.id FROM merchant_pg pg "
                        + "JOIN store s ON s.merchant_id = pg.merchant_id "
                        + "WHERE s.id = ? AND pg.pg_provider = 'FAKE' AND pg.status = 'ACTIVE'",
                (rs, row) -> new Config(rs.getLong("id")), storeId).stream().findFirst();
    }
}
