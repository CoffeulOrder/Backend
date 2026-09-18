package com.coffeul.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * 통합 테스트용 최소 데이터 시딩 헬퍼. 실제 사업자 정보(merchant)는 여전히 가짜 값이며,
 * 프로덕션 시드(menu-seed.sql)와는 별개로 각 테스트가 필요한 것만 최소로 심는다.
 */
public class TestFixtures {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final JdbcTemplate jdbc;

    public TestFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long createSchool(String name) {
        return insert("INSERT INTO `school` (`name`, `campus`, `status`) VALUES (?, ?, 'ACTIVE')",
                name, "테스트캠퍼스");
    }

    public int countMembersByEmail(String email) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM `member` WHERE `email` = ?",
                Integer.class, email);
        return count == null ? 0 : count;
    }

    public int countTermsAgreements(long memberId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM `terms_agreement` WHERE `member_id` = ?",
                Integer.class, memberId);
        return count == null ? 0 : count;
    }

    /**
     * 인증 토큰이 실제로 소비됐는지 (REQ-EV-005).
     * <p>DATETIME은 시간대가 없어서 드라이버가 Instant로 바로 못 준다. 접속 문자열이 serverTimezone=UTC라
     * 읽어온 값을 UTC로 해석한다 (AbstractIntegrationTest의 컨테이너 설정과 같은 전제).
     */
    public Instant consumedAtOf(String email) {
        Timestamp consumedAt = jdbc.queryForObject(
                "SELECT `consumed_at` FROM `email_verification` WHERE `email` = ? ORDER BY `created_at` DESC LIMIT 1",
                Timestamp.class, email);
        return consumedAt == null ? null : consumedAt.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    public long createSchoolEmailDomain(long schoolId, String domain) {
        return insert("INSERT INTO `school_email_domain` (`school_id`, `domain`) VALUES (?, ?)", schoolId, domain);
    }

    public long insertEmailVerification(String email, String purpose, String codeHash,
                                         Instant expiresAt, Instant createdAt) {
        return insert("INSERT INTO `email_verification` (`email`, `purpose`, `code_hash`, `expires_at`, `created_at`) " +
                        "VALUES (?, ?, ?, ?, ?)",
                email, purpose, codeHash, expiresAt, createdAt);
    }

    public int countEmailVerifications(String email) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM `email_verification` WHERE `email` = ?",
                Integer.class, email);
        return count == null ? 0 : count;
    }

    public int attemptCountOf(String email) {
        Integer count = jdbc.queryForObject(
                "SELECT `attempt_count` FROM `email_verification` WHERE `email` = ? ORDER BY `created_at` DESC LIMIT 1",
                Integer.class, email);
        return count == null ? 0 : count;
    }

    public String tokenHashOf(String email) {
        return jdbc.queryForObject(
                "SELECT `token_hash` FROM `email_verification` WHERE `email` = ? ORDER BY `created_at` DESC LIMIT 1",
                String.class, email);
    }

    /** 인증 토큰 만료(30분)를 실제로 기다리지 않고 검증하기 위한 헬퍼. */
    public void expireVerificationToken(String email, Instant tokenExpiresAt) {
        jdbc.update("UPDATE `email_verification` SET `token_expires_at` = ? WHERE `email` = ?",
                tokenExpiresAt, email);
    }

    /** 60초 재발송 제한 · 만료를 테스트에서 실제로 기다리지 않고 검증하기 위한 헬퍼. */
    public void ageEmailVerification(String email, Instant createdAt, Instant expiresAt) {
        jdbc.update("UPDATE `email_verification` SET `created_at` = ?, `expires_at` = ? WHERE `email` = ?",
                createdAt, expiresAt, email);
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

    public long createMemberWithPassword(long schoolId, String email, String rawPassword, String status) {
        return insert("INSERT INTO `member` (`school_id`, `email`, `password_hash`, `name`, `status`) " +
                        "VALUES (?, ?, ?, ?, ?)",
                schoolId, email, PASSWORD_ENCODER.encode(rawPassword), "테스트회원", status);
    }

    public long createStaffAccount(Long merchantId, String role, String loginId, String rawPassword,
                                    String name, String status) {
        return insert("INSERT INTO `staff_account` (`merchant_id`, `role`, `login_id`, `password_hash`, " +
                        "`name`, `status`) VALUES (?, ?, ?, ?, ?, ?)",
                merchantId, role, loginId, PASSWORD_ENCODER.encode(rawPassword), name, status);
    }

    public void createStaffStore(long staffAccountId, long storeId) {
        jdbc.update("INSERT INTO `staff_store` (`staff_account_id`, `store_id`) VALUES (?, ?)",
                staffAccountId, storeId);
    }

    public int countRefreshTokensBySubject(String subjectType, long subjectId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM `refresh_token` WHERE `subject_type` = ? AND `subject_id` = ?",
                Integer.class, subjectType, subjectId);
        return count == null ? 0 : count;
    }

    public int countActiveRefreshTokensBySubject(String subjectType, long subjectId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM `refresh_token` WHERE `subject_type` = ? AND `subject_id` = ? AND `revoked_at` IS NULL",
                Integer.class, subjectType, subjectId);
        return count == null ? 0 : count;
    }

    /** 재사용 감지의 30초 유예 창을 테스트에서 실제로 기다리지 않고 검증하기 위한 헬퍼. */
    public void ageRefreshTokenRevocation(String tokenHash, Instant revokedAt) {
        jdbc.update("UPDATE `refresh_token` SET `revoked_at` = ? WHERE `token_hash` = ?", revokedAt, tokenHash);
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
