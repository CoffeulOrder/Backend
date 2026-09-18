package com.coffeul.auth;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.infrastructure.TokenHasher;
import com.coffeul.support.TestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MS-1~4(고객 로그인 · 직원 로그인 · 토큰 재발급 · 로그아웃) 엔드투엔드 검증.
 * 인증 관련 코드는 신중하게 가야 해서, 잠금 · 재사용 감지 같은 규칙(rules.py AUTH_SPEC)을
 * 실제 MySQL로 끝까지 확인한다.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private TestFixtures fixtures;
    private long schoolId;
    private long merchantId;
    private long storeId;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        String unique = java.util.UUID.randomUUID().toString().substring(0, 8);
        schoolId = fixtures.createSchool("테스트대학교-" + unique);
        merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & 0x7fffffff), new BigDecimal("0.0300"));
        storeId = fixtures.createStore(merchantId, schoolId, "테스트매장-" + unique, "OPEN");
    }

    @Test
    void 고객_로그인에_성공하면_토큰과_refresh_토큰_행이_생긴다() throws Exception {
        long memberId = fixtures.createMemberWithPassword(schoolId, "student@test.ac.kr", "coffee1234", "ACTIVE");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "student@test.ac.kr", "password", "coffee1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member.memberId").value(memberId))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).isNotBlank();
        assertThat(fixtures.countActiveRefreshTokensBySubject("MEMBER", memberId)).isEqualTo(1);
    }

    @Test
    void 비밀번호가_틀리면_401이고_5번째부터_잠긴다() throws Exception {
        long memberId = fixtures.createMemberWithPassword(schoolId, "lockme@test.ac.kr", "correct-password", "ACTIVE");

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", "lockme@test.ac.kr", "password", "wrong"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_001"));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "lockme@test.ac.kr", "password", "wrong"))))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "lockme@test.ac.kr", "password", "correct-password"))))
                .andExpect(status().is(423));

        assertThat(fixtures.countRefreshTokensBySubject("MEMBER", memberId)).isEqualTo(0);
    }

    @Test
    void 직원_로그인에_성공하면_접근_가능한_매장_목록을_같이_받는다() throws Exception {
        long staffId = fixtures.createStaffAccount(merchantId, "STAFF", "bs-tablet", "tablet-pw-1234", "범석관 태블릿", "ACTIVE");
        fixtures.createStaffStore(staffId, storeId);

        mockMvc.perform(post("/api/v1/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("loginId", "bs-tablet", "password", "tablet-pw-1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staff.staffId").value(staffId))
                .andExpect(jsonPath("$.data.staff.role").value("STAFF"))
                .andExpect(jsonPath("$.data.stores[0].storeId").value(storeId))
                .andExpect(jsonPath("$.data.stores[0].status").value("OPEN"));
    }

    @Test
    void 토큰을_재발급하면_이전_refresh는_폐기되고_새_쌍이_나온다() throws Exception {
        fixtures.createMemberWithPassword(schoolId, "refresh@test.ac.kr", "coffee1234", "ACTIVE");
        String firstRefreshToken = login("refresh@test.ac.kr", "coffee1234");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefreshToken))))
                .andExpect(status().isOk())
                .andReturn();
        String newRefreshToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(firstRefreshToken);

        // 재발급 재사용 — 30초 이내라 동시 요청으로 보고 새 쌍만 발급 (계열 폐기 아님, rules.py AUTH_SPEC)
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefreshToken))))
                .andExpect(status().isOk());

        // 30초가 지난 뒤 재사용되면 탈취로 보고 같은 계열 전부 폐기
        fixtures.ageRefreshTokenRevocation(TokenHasher.sha256Hex(firstRefreshToken), Instant.now().minusSeconds(31));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", newRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void 로그아웃하면_그_refresh_토큰은_폐기된다() throws Exception {
        fixtures.createMemberWithPassword(schoolId, "logout@test.ac.kr", "coffee1234", "ACTIVE");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "logout@test.ac.kr", "password", "coffee1234"))))
                .andReturn();
        JsonNode data = objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data");
        String accessToken = data.path("accessToken").asText();
        String refreshToken = data.path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk());

        // 이미 폐기된 refresh로 다시 로그아웃해도 멱등하게 200
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("refreshToken").asText();
    }
}
