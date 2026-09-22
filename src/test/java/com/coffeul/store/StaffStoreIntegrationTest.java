package com.coffeul.store;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MS-7(내가 관리하는 매장) · MS-8(영업 상태 변경) 엔드투엔드 검증. */
class StaffStoreIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    private TestFixtures fixtures;
    private long schoolId;
    private long merchantId;
    private long storeId;
    private long memberId;
    private String staffAuthHeader;
    private String ownerAuthHeader;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        schoolId = fixtures.createSchool("테스트대학교-" + unique);
        merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & 0x7fffffff), new BigDecimal("0.0300"));
        storeId = fixtures.createStore(merchantId, schoolId, "테스트매장", "OPEN");
        memberId = fixtures.createMember(schoolId, "member-" + unique + "@test.ac.kr", "ACTIVE");

        long staffId = fixtures.createStaffAccount(merchantId, "STAFF", "staff-" + unique, "pw1234!!", "테스트직원", "ACTIVE");
        fixtures.createStaffStore(staffId, storeId);
        staffAuthHeader = authHeaderFor(new AuthUser(staffId, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, merchantId));

        long ownerId = fixtures.createStaffAccount(merchantId, "OWNER", "owner-" + unique, "pw1234!!", "테스트사장", "ACTIVE");
        ownerAuthHeader = authHeaderFor(new AuthUser(ownerId, AuthUser.SubjectType.STAFF, AuthUser.Role.OWNER, null, merchantId));
    }

    private String authHeaderFor(AuthUser user) {
        return "Bearer " + jwtAccessTokenService.encode(user, Instant.now());
    }

    @Test
    void STAFF는_배정된_매장만_받는다() throws Exception {
        mockMvc.perform(get("/api/v1/staff/stores").header("Authorization", staffAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].storeId").value(storeId))
                .andExpect(jsonPath("$.data[0].schoolName").exists());
    }

    @Test
    void OWNER는_자기_매장_전부를_받는다() throws Exception {
        fixtures.createStore(merchantId, schoolId, "두번째매장", "PAUSED");

        mockMvc.perform(get("/api/v1/staff/stores").header("Authorization", ownerAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void 고객_토큰으로_호출하면_403이다() throws Exception {
        String customerAuth = authHeaderFor(new AuthUser(memberId, AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, schoolId, null));

        mockMvc.perform(get("/api/v1/staff/stores").header("Authorization", customerAuth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    @Test
    void 영업상태를_바꾸면_저장된다() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("status", "PAUSED", "force", false));

        mockMvc.perform(patch("/api/v1/staff/stores/{storeId}/status", storeId)
                        .header("Authorization", staffAuthHeader)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));

        String status = jdbcTemplate.queryForObject("SELECT `status` FROM `store` WHERE `id` = ?", String.class, storeId);
        assertThat(status).isEqualTo("PAUSED");
    }

    @Test
    void 처리중_주문이_있으면_마감이_막힌다() throws Exception {
        fixtures.createOrder(memberId, storeId, schoolId, "REQUESTED");
        String body = objectMapper.writeValueAsString(Map.of("status", "CLOSED", "force", false));

        mockMvc.perform(patch("/api/v1/staff/stores/{storeId}/status", storeId)
                        .header("Authorization", staffAuthHeader)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ST002"))
                .andExpect(jsonPath("$.data.activeOrderCount").value(1));
    }

    @Test
    void force면_처리중_주문이_있어도_마감된다() throws Exception {
        fixtures.createOrder(memberId, storeId, schoolId, "REQUESTED");
        String body = objectMapper.writeValueAsString(Map.of("status", "CLOSED", "force", true));

        mockMvc.perform(patch("/api/v1/staff/stores/{storeId}/status", storeId)
                        .header("Authorization", staffAuthHeader)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void 잘못된_상태값이면_400이다() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("status", "FOO", "force", false));

        mockMvc.perform(patch("/api/v1/staff/stores/{storeId}/status", storeId)
                        .header("Authorization", staffAuthHeader)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ST003"));
    }

    @Test
    void 다른_사장님_직원이면_403이다() throws Exception {
        long otherMerchantId = fixtures.createMerchant(
                String.format("%010d", UUID.randomUUID().toString().hashCode() & 0x7fffffff), new BigDecimal("0.0300"));
        long otherStaffId = fixtures.createStaffAccount(otherMerchantId, "STAFF", "other-" + UUID.randomUUID(),
                "pw1234!!", "다른매장직원", "ACTIVE");
        String otherStaffAuth = authHeaderFor(new AuthUser(otherStaffId, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, otherMerchantId));
        String body = objectMapper.writeValueAsString(Map.of("status", "PAUSED", "force", false));

        mockMvc.perform(patch("/api/v1/staff/stores/{storeId}/status", storeId)
                        .header("Authorization", otherStaffAuth)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test
    void ADMIN도_바꿀_수_있다() throws Exception {
        String adminAuth = authHeaderFor(new AuthUser(1L, AuthUser.SubjectType.STAFF, AuthUser.Role.ADMIN, null, null));
        String body = objectMapper.writeValueAsString(Map.of("status", "PAUSED", "force", false));

        mockMvc.perform(patch("/api/v1/staff/stores/{storeId}/status", storeId)
                        .header("Authorization", adminAuth)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
