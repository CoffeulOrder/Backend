package com.coffeul.store;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MS-5(우리 학교 매장 목록) · MS-6(매장 상세 · 판매자 정보) 엔드투엔드 검증. */
class StoreIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    private TestFixtures fixtures;
    private long mySchoolId;
    private long myStoreId;
    private String customerAuthHeader;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        mySchoolId = fixtures.createSchool("테스트대학교-" + unique);
        long merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & 0x7fffffff),
                new BigDecimal("0.0300"));
        myStoreId = fixtures.createStore(merchantId, mySchoolId, "테스트매장", "OPEN");
        long memberId = fixtures.createMember(mySchoolId, "member-" + unique + "@test.ac.kr", "ACTIVE");
        customerAuthHeader = "Bearer " + jwtAccessTokenService.encode(
                new AuthUser(memberId, AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, mySchoolId, null), Instant.now());
    }

    @Test
    void 매장_목록을_불러오면_내_학교_매장만_나온다() throws Exception {
        long otherSchoolId = fixtures.createSchool("다른대학교-" + UUID.randomUUID().toString().substring(0, 8));
        long otherMerchantId = fixtures.createMerchant(
                String.format("%010d", UUID.randomUUID().toString().hashCode() & 0x7fffffff), new BigDecimal("0.0300"));
        fixtures.createStore(otherMerchantId, otherSchoolId, "다른학교매장", "OPEN");

        mockMvc.perform(get("/api/v1/stores").header("Authorization", customerAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].storeId").value(myStoreId))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"));
    }

    @Test
    void 매장_상세를_불러오면_판매자_정보와_운영시간을_포함한다() throws Exception {
        fixtures.setStoreLocationAndNotice(myStoreId, "범석관 1층", "시험기간에는 22시까지 운영해요.");
        fixtures.createBusinessHour(myStoreId, 1, LocalTime.of(8, 30), LocalTime.of(18, 0), false);
        fixtures.createBusinessHour(myStoreId, 7, null, null, true);

        mockMvc.perform(get("/api/v1/stores/{storeId}", myStoreId).header("Authorization", customerAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(myStoreId))
                .andExpect(jsonPath("$.data.location").value("범석관 1층"))
                .andExpect(jsonPath("$.data.notice").value("시험기간에는 22시까지 운영해요."))
                .andExpect(jsonPath("$.data.businessHours.length()").value(2))
                .andExpect(jsonPath("$.data.businessHours[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.data.businessHours[0].openTime").value("08:30:00"))
                .andExpect(jsonPath("$.data.businessHours[1].closed").value(true))
                .andExpect(jsonPath("$.data.seller.businessName").value("테스트상호"))
                .andExpect(jsonPath("$.data.seller.representativeName").value("테스트대표"));
    }

    @Test
    void 다른_학교_매장은_404다() throws Exception {
        long otherSchoolId = fixtures.createSchool("다른대학교-" + UUID.randomUUID().toString().substring(0, 8));
        long otherMerchantId = fixtures.createMerchant(
                String.format("%010d", UUID.randomUUID().toString().hashCode() & 0x7fffffff), new BigDecimal("0.0300"));
        long otherStoreId = fixtures.createStore(otherMerchantId, otherSchoolId, "다른학교매장", "OPEN");

        mockMvc.perform(get("/api/v1/stores/{storeId}", otherStoreId).header("Authorization", customerAuthHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ST001"));
    }

    @Test
    void 없는_매장은_404다() throws Exception {
        mockMvc.perform(get("/api/v1/stores/{storeId}", 999_999_999L).header("Authorization", customerAuthHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ST001"));
    }

    @Test
    void 직원_토큰으로_호출하면_403이다() throws Exception {
        String staffAuth = "Bearer " + jwtAccessTokenService.encode(
                new AuthUser(1L, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, 1L), Instant.now());

        mockMvc.perform(get("/api/v1/stores").header("Authorization", staffAuth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C003"));
    }
}
