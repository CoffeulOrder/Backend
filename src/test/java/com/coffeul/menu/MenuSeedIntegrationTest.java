package com.coffeul.menu;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-MN-006: menu-seed.sql이 실제 MySQL 8.4 컨테이너에서 그대로 돌아가는지 확인한다
 * (menu_seed.py 문서 수치와 정확히 일치해야 함 — 프로덕션 시드 자체는 이 테스트에서 건드리지 않는다).
 */
class MenuSeedIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;

    @Test
    void 메뉴_시드_SQL을_실행하면_41개_메뉴가_들어간다() throws Exception {
        TestFixtures fixtures = new TestFixtures(jdbcTemplate);
        long schoolId = fixtures.createSchool("을지대학교");
        long merchantId = fixtures.createMerchant("2345678901", new BigDecimal("0.0300"));
        long storeId = fixtures.createStore(merchantId, schoolId, "범석관점", "OPEN");

        ClassPathResource seedResource = new ClassPathResource("db/seed/menu-seed.sql");
        String seedSql = Files.readString(seedResource.getFile().toPath(), StandardCharsets.UTF_8);
        String combinedSql = "SET @coffeul_store_id = " + storeId + ";\n" + seedSql;

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new org.springframework.core.io.support.EncodedResource(
                            new org.springframework.core.io.ByteArrayResource(
                                    combinedSql.getBytes(StandardCharsets.UTF_8)),
                            StandardCharsets.UTF_8));
        }

        Integer menuCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `menu_item` WHERE `store_id` = ?", Integer.class, storeId);
        assertThat(menuCount).isEqualTo(41);

        Integer categoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `category` WHERE `store_id` = ?", Integer.class, storeId);
        assertThat(categoryCount).isEqualTo(7);

        Integer optionItemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `option_item` oi " +
                        "JOIN `option_group` og ON oi.option_group_id = og.id " +
                        "JOIN `menu_item` mi ON og.menu_item_id = mi.id " +
                        "WHERE mi.store_id = ?", Integer.class, storeId);
        assertThat(optionItemCount).isEqualTo(105);
    }
}
