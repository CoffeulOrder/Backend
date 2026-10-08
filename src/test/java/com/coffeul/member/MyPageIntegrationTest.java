package com.coffeul.member;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SM-4(내 정보) · SM-5(비밀번호 변경) · SM-7(탈퇴) — REQ-U-004 · 005 · 007. */
class MyPageIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    private TestFixtures fixtures;
    private long schoolId;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        schoolId = fixtures.createSchool("을지대학교-" + UUID.randomUUID().toString().substring(0, 8));
    }

    // ---------- SM-4 ----------

    @Test
    void 내_정보를_불러오면_이름_이메일_학교를_돌려준다() throws Exception {
        String email = uniqueEmail();
        long memberId = fixtures.createMemberWithPassword(schoolId, email, "coffee1234", "ACTIVE");

        mockMvc.perform(get("/api/v1/members/me").header("Authorization", bearerFor(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.memberId").value((int) memberId))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.school.schoolId").value((int) schoolId))
                .andExpect(jsonPath("$.data.school.campus").value("테스트캠퍼스"))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void 토큰이_없으면_C002다() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    void 직원_토큰으로는_내_정보를_볼_수_없다() throws Exception {
        // 직원 토큰의 sub는 staff_account.id다. 주체 종류를 안 보면 같은 숫자의 member를 남의 정보로 읽는다.
        long memberId = fixtures.createMemberWithPassword(schoolId, uniqueEmail(), "coffee1234", "ACTIVE");
        AuthUser staff = new AuthUser(memberId, AuthUser.SubjectType.STAFF, AuthUser.Role.OWNER, null, 1L);
        String staffToken = jwtAccessTokenService.encode(staff, Instant.now());

        mockMvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    @Test
    void 탈퇴한_회원의_남은_토큰으로는_내_정보를_볼_수_없다() throws Exception {
        long memberId = fixtures.createMemberWithPassword(schoolId, uniqueEmail(), "coffee1234", "ACTIVE");
        String token = bearerFor(memberId);
        withdraw(memberId, token, "coffee1234").andExpect(status().isOk());

        // access 토큰은 최대 1시간 더 살아 있다 (REQ-AUTH-012).
        mockMvc.perform(get("/api/v1/members/me").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    // ---------- SM-5 ----------

    @Test
    void 비밀번호를_바꾸면_새_비밀번호로_로그인되고_기존_세션은_끊긴다() throws Exception {
        String email = uniqueEmail();
        long memberId = fixtures.createMemberWithPassword(schoolId, email, "coffee1234", "ACTIVE");
        login(email, "coffee1234").andExpect(status().isOk());
        assertThat(fixtures.countActiveRefreshTokensBySubject("MEMBER", memberId)).isEqualTo(1);

        mockMvc.perform(patch("/api/v1/members/me/password")
                        .header("Authorization", bearerFor(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", "coffee1234", "newPassword", "latte5678"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("비밀번호를 바꿨어요. 다시 로그인해주세요."));

        assertThat(fixtures.countActiveRefreshTokensBySubject("MEMBER", memberId)).isZero();
        login(email, "latte5678").andExpect(status().isOk());
        login(email, "coffee1234").andExpect(status().isUnauthorized());
    }

    @Test
    void 현재_비밀번호가_틀리면_U004다() throws Exception {
        long memberId = fixtures.createMemberWithPassword(schoolId, uniqueEmail(), "coffee1234", "ACTIVE");

        mockMvc.perform(patch("/api/v1/members/me/password")
                        .header("Authorization", bearerFor(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", "wrong1234", "newPassword", "latte5678"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("U004"));
    }

    @Test
    void 새_비밀번호가_규칙에_맞지_않으면_U002다() throws Exception {
        long memberId = fixtures.createMemberWithPassword(schoolId, uniqueEmail(), "coffee1234", "ACTIVE");

        mockMvc.perform(patch("/api/v1/members/me/password")
                        .header("Authorization", bearerFor(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("currentPassword", "coffee1234", "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("U002"));
    }

    // ---------- SM-7 ----------

    @Test
    void 탈퇴하면_개인정보가_지워지고_토큰이_전부_정리된다() throws Exception {
        String email = uniqueEmail();
        long memberId = fixtures.createMemberWithPassword(schoolId, email, "coffee1234", "ACTIVE");
        login(email, "coffee1234").andExpect(status().isOk());
        fixtures.createDeviceToken("MEMBER", memberId);

        withdraw(memberId, bearerFor(memberId), "coffee1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("탈퇴했어요. 그동안 이용해주셔서 고마워요."));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT `email`, `name`, `status`, `password_hash`, `withdrawn_at` FROM `member` WHERE `id` = ?",
                memberId);
        assertThat(row.get("email")).isNull();
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("name")).isEqualTo("탈퇴회원");
        assertThat(row.get("status")).isEqualTo("WITHDRAWN");
        assertThat(row.get("withdrawn_at")).isNotNull();

        assertThat(fixtures.countActiveRefreshTokensBySubject("MEMBER", memberId)).isZero();
        assertThat(fixtures.countActiveDeviceTokens("MEMBER", memberId)).isZero();
    }

    @Test
    void 진행_중_주문이_있으면_U005와_주문_수를_돌려준다() throws Exception {
        long memberId = fixtures.createMemberWithPassword(schoolId, uniqueEmail(), "coffee1234", "ACTIVE");
        long merchantId = fixtures.createMerchant(String.valueOf(System.nanoTime()).substring(0, 10),
                new java.math.BigDecimal("0.0500"));
        long storeId = fixtures.createStore(merchantId, schoolId, "범석관", "OPEN");
        fixtures.createOrder(memberId, storeId, schoolId, "ACCEPTED");

        withdraw(memberId, bearerFor(memberId), "coffee1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("U005"))
                .andExpect(jsonPath("$.data.activeOrderCount").value(1));

        // 막혔으면 계정은 그대로여야 한다.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT `status` FROM `member` WHERE `id` = ?", String.class, memberId)).isEqualTo("ACTIVE");
    }

    @Test
    void 끝난_주문만_있으면_탈퇴할_수_있다() throws Exception {
        long memberId = fixtures.createMemberWithPassword(schoolId, uniqueEmail(), "coffee1234", "ACTIVE");
        long merchantId = fixtures.createMerchant(String.valueOf(System.nanoTime()).substring(0, 10),
                new java.math.BigDecimal("0.0500"));
        long storeId = fixtures.createStore(merchantId, schoolId, "범석관", "OPEN");
        fixtures.createOrder(memberId, storeId, schoolId, "COMPLETED");
        fixtures.createOrder(memberId, storeId, schoolId, "CANCELED");

        withdraw(memberId, bearerFor(memberId), "coffee1234").andExpect(status().isOk());

        // 주문 · 결제 기록은 5년 보존이라 행이 남아 있어야 한다 (전자상거래법 시행령 제6조).
        assertThat(fixtures.countOrdersByMember(memberId)).isEqualTo(2);
    }

    @Test
    void 비밀번호가_틀리면_탈퇴되지_않는다() throws Exception {
        long memberId = fixtures.createMemberWithPassword(schoolId, uniqueEmail(), "coffee1234", "ACTIVE");

        withdraw(memberId, bearerFor(memberId), "wrong1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("U004"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT `status` FROM `member` WHERE `id` = ?", String.class, memberId)).isEqualTo("ACTIVE");
    }

    @Test
    void 탈퇴한_이메일로_다시_가입할_수_있다() throws Exception {
        // email을 NULL로 비우는 이유가 이것 — uk_member_email에 걸리지 않아야 재가입이 된다.
        String email = uniqueEmail();
        long memberId = fixtures.createMemberWithPassword(schoolId, email, "coffee1234", "ACTIVE");
        withdraw(memberId, bearerFor(memberId), "coffee1234").andExpect(status().isOk());

        long rejoined = fixtures.createMemberWithPassword(schoolId, email, "coffee1234", "ACTIVE");

        assertThat(rejoined).isNotEqualTo(memberId);
        assertThat(fixtures.countMembersByEmail(email)).isEqualTo(1);
    }

    // ---------- helpers ----------

    private String uniqueEmail() {
        return "me-" + UUID.randomUUID().toString().substring(0, 8) + "@test.ac.kr";
    }

    private String bearerFor(long memberId) {
        AuthUser member = new AuthUser(memberId, AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, schoolId, null);
        return "Bearer " + jwtAccessTokenService.encode(member, Instant.now());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private org.springframework.test.web.servlet.ResultActions withdraw(long memberId, String bearer, String password)
            throws Exception {
        return mockMvc.perform(delete("/api/v1/members/me")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("password", password))));
    }
}
