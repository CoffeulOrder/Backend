package com.coffeul.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * 통합 테스트용 최소 데이터 시딩 헬퍼. 실제 사업자 정보(merchant)는 여전히 가짜 값이며,
 * 프로덕션 시드(menu-seed.sql)와는 별개로 각 테스트가 필요한 것만 최소로 심는다.
 */
public class TestFixtures {

    private final JdbcTemplate jdbc;

    public TestFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long createSchool(String name) {
        return insert("INSERT INTO `school` (`name`, `campus`, `status`) VALUES (?, ?, 'ACTIVE')",
                name, "테스트캠퍼스");
    }

    public long createMerchant(String businessRegNo, BigDecimal commissionRate) {
        return insert("INSERT INTO `merchant` (`business_name`, `business_reg_no`, `representative_name`, " +
                        "`business_address`, `contact_phone`, `commission_rate`, `status`) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')",
                "테스트상호", businessRegNo, "테스트대표", "테스트주소", "010-0000-0000", commissionRate);
    }

    public long createStore(long merchantId, long schoolId, String name, String status) {
        return insert("INSERT INTO `store` (`merchant_id`, `school_id`, `name`, `status`) VALUES (?, ?, ?, ?)",
                merchantId, schoolId, name, status);
    }

    public long createMember(long schoolId, String email, String status) {
        return insert("INSERT INTO `member` (`school_id`, `email`, `password_hash`, `name`, `status`) " +
                        "VALUES (?, ?, ?, ?, ?)",
                schoolId, email, "{bcrypt}테스트해시", "테스트회원", status);
    }

    public long createCategory(long storeId, String name) {
        return insert("INSERT INTO `category` (`store_id`, `name`) VALUES (?, ?)", storeId, name);
    }

    public long createMenuItem(long storeId, String name, int basePrice, boolean soldOut) {
        return insert("INSERT INTO `menu_item` (`store_id`, `name`, `base_price`, `is_sold_out`) VALUES (?, ?, ?, ?)",
                storeId, name, basePrice, soldOut);
    }

    public void linkMenuItemToCategory(long menuItemId, long categoryId) {
        jdbc.update("INSERT INTO `menu_item_category` (`menu_item_id`, `category_id`) VALUES (?, ?)",
                menuItemId, categoryId);
    }

    public long createOptionGroup(long menuItemId, String name, String optionType,
                                   boolean required, int minSelect, int maxSelect) {
        return insert("INSERT INTO `option_group` (`menu_item_id`, `name`, `option_type`, `is_required`, " +
                        "`min_select`, `max_select`) VALUES (?, ?, ?, ?, ?, ?)",
                menuItemId, name, optionType, required, minSelect, maxSelect);
    }

    public long createOptionItem(long optionGroupId, String name, int priceDelta, boolean isDefault) {
        return insert("INSERT INTO `option_item` (`option_group_id`, `name`, `price_delta`, `is_default`) " +
                        "VALUES (?, ?, ?, ?)",
                optionGroupId, name, priceDelta, isDefault);
    }

    public int countOrdersByMember(long memberId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM `orders` WHERE `member_id` = ?", Integer.class, memberId);
        return count == null ? 0 : count;
    }

    private long insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
