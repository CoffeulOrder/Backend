package com.coffeul.store;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.store.api.StoreAccessPolicy;
import com.coffeul.store.api.StoreErrorCode;
import com.coffeul.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * store.api.StoreAccessPolicy 검증 (rules.py AUTH_SPEC "매장 권한" — ADMIN 통과 · OWNER는 merchant 일치 ·
 * STAFF는 staff_store + 계정 ACTIVE). order.MS-22~26을 비롯해 매장용 API가 전부 이 판정을 그대로 쓴다.
 */
class StoreAccessPolicyTest extends AbstractIntegrationTest {

    @Autowired
    private StoreAccessPolicy storeAccessPolicy;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestFixtures fixtures;
    private long myMerchantId;
    private long myStoreId;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        long schoolId = fixtures.createSchool("테스트대학교-" + unique);
        myMerchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & 0x7fffffff),
                new BigDecimal("0.0300"));
        myStoreId = fixtures.createStore(myMerchantId, schoolId, "테스트매장", "OPEN");
    }

    @Test
    void ADMIN은_아무_매장이나_통과한다() {
        AuthUser admin = new AuthUser(1L, AuthUser.SubjectType.STAFF, AuthUser.Role.ADMIN, null, null);

        assertThatCode(() -> storeAccessPolicy.check(admin, myStoreId)).doesNotThrowAnyException();
    }

    @Test
    void OWNER는_자기_merchant_매장만_통과한다() {
        AuthUser owner = new AuthUser(1L, AuthUser.SubjectType.STAFF, AuthUser.Role.OWNER, null, myMerchantId);

        assertThatCode(() -> storeAccessPolicy.check(owner, myStoreId)).doesNotThrowAnyException();
    }

    @Test
    void OWNER는_다른_merchant_매장이면_403이다() {
        long otherMerchantId = fixtures.createMerchant(
                String.format("%010d", UUID.randomUUID().toString().hashCode() & 0x7fffffff), new BigDecimal("0.0300"));
        AuthUser otherOwner = new AuthUser(1L, AuthUser.SubjectType.STAFF, AuthUser.Role.OWNER, null, otherMerchantId);

        assertThatThrownBy(() -> storeAccessPolicy.check(otherOwner, myStoreId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(StoreErrorCode.FORBIDDEN);
    }

    @Test
    void STAFF는_staff_store에_배정된_매장만_통과한다() {
        long staffId = fixtures.createStaffAccount(myMerchantId, "STAFF", "staff-" + UUID.randomUUID(), "pw1234!!",
                "테스트직원", "ACTIVE");
        fixtures.createStaffStore(staffId, myStoreId);
        AuthUser staff = new AuthUser(staffId, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, myMerchantId);

        assertThatCode(() -> storeAccessPolicy.check(staff, myStoreId)).doesNotThrowAnyException();
    }

    @Test
    void STAFF는_배정되지_않은_매장이면_403이다() {
        long staffId = fixtures.createStaffAccount(myMerchantId, "STAFF", "staff-" + UUID.randomUUID(), "pw1234!!",
                "테스트직원", "ACTIVE");
        // staff_store 행을 만들지 않음 — 배정 없음
        AuthUser staff = new AuthUser(staffId, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, myMerchantId);

        assertThatThrownBy(() -> storeAccessPolicy.check(staff, myStoreId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(StoreErrorCode.FORBIDDEN);
    }

    @Test
    void STAFF는_계정이_ACTIVE가_아니면_403이다() {
        long staffId = fixtures.createStaffAccount(myMerchantId, "STAFF", "staff-" + UUID.randomUUID(), "pw1234!!",
                "테스트직원", "INACTIVE");
        fixtures.createStaffStore(staffId, myStoreId);
        AuthUser staff = new AuthUser(staffId, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, myMerchantId);

        assertThatThrownBy(() -> storeAccessPolicy.check(staff, myStoreId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(StoreErrorCode.FORBIDDEN);
    }

    @Test
    void CUSTOMER는_항상_403이다() {
        AuthUser customer = new AuthUser(1L, AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, 1L, null);

        assertThatThrownBy(() -> storeAccessPolicy.check(customer, myStoreId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(StoreErrorCode.FORBIDDEN);
    }
}
