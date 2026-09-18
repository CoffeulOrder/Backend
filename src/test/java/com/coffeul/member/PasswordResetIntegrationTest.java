package com.coffeul.member;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.support.MailTestConfig;
import com.coffeul.support.RecordingMailSender;
import com.coffeul.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SM-6(비밀번호 재설정) 엔드투엔드 검증 — REQ-U-006. */
@Import(MailTestConfig.class)
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RecordingMailSender mailSender;

    private TestFixtures fixtures;
    private long schoolId;
    private String email;
    private long memberId;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        mailSender.reset();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        schoolId = fixtures.createSchool("테스트대학교-" + unique);
        String domain = "s" + unique + ".test.ac.kr";
        fixtures.createSchoolEmailDomain(schoolId, domain);
        email = "student@" + domain;
        memberId = fixtures.createMemberWithPassword(schoolId, email, "coffee1234", "ACTIVE");
    }

    @Test
    void 재설정하면_새_비밀번호로_로그인되고_기존_비밀번호는_막힌다() throws Exception {
        String token = issuePasswordResetToken();

        mockMvc.perform(resetRequest(token, "latte5678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("비밀번호를 재설정했어요. 새 비밀번호로 로그인해주세요."));

        login("latte5678").andExpect(status().isOk());
        login("coffee1234").andExpect(status().isUnauthorized());
    }

    @Test
    void 재설정하면_기존_refresh_토큰이_전부_폐기된다() throws Exception {
        // 다른 기기 두 곳에서 로그인해둔 상태.
        login("coffee1234").andExpect(status().isOk());
        login("coffee1234").andExpect(status().isOk());
        assertThat(fixtures.countActiveRefreshTokensBySubject("MEMBER", memberId)).isEqualTo(2);

        mockMvc.perform(resetRequest(issuePasswordResetToken(), "latte5678"))
                .andExpect(status().isOk());

        assertThat(fixtures.countActiveRefreshTokensBySubject("MEMBER", memberId)).isZero();
    }

    @Test
    void 재설정하면_로그인_잠금이_풀린다() throws Exception {
        // 5회 틀려서 15분 잠긴 상태를 만든다 (rules.py AUTH_SPEC).
        for (int i = 0; i < 5; i++) {
            login("wrong-password1");
        }
        login("coffee1234").andExpect(status().isLocked());

        mockMvc.perform(resetRequest(issuePasswordResetToken(), "latte5678"))
                .andExpect(status().isOk());

        // 잠금이 안 풀리면 재설정하고도 15분을 더 기다려야 해서 재설정한 의미가 없다.
        login("latte5678").andExpect(status().isOk());
    }

    @Test
    void 새_비밀번호가_규칙에_맞지_않으면_U002이고_인증_토큰은_살아_있다() throws Exception {
        String token = issuePasswordResetToken();

        mockMvc.perform(resetRequest(token, "short1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("U002"));

        assertThat(fixtures.consumedAtOf(email)).isNull();
        // 규칙에 맞는 값으로 다시 내면 같은 토큰이 그대로 통해야 한다.
        mockMvc.perform(resetRequest(token, "latte5678")).andExpect(status().isOk());
    }

    @Test
    void 이미_쓴_인증_토큰이면_EV008() throws Exception {
        String token = issuePasswordResetToken();
        mockMvc.perform(resetRequest(token, "latte5678")).andExpect(status().isOk());

        mockMvc.perform(resetRequest(token, "mocha9012"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EV008"));

        // 두 번째 시도는 반영되지 않아야 한다.
        login("latte5678").andExpect(status().isOk());
    }

    @Test
    void 만료된_인증_토큰이면_EV008() throws Exception {
        String token = issuePasswordResetToken();
        fixtures.expireVerificationToken(email, Instant.now().minus(1, ChronoUnit.MINUTES));

        mockMvc.perform(resetRequest(token, "latte5678"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EV008"));
    }

    @Test
    void 가입용_토큰으로는_재설정할_수_없다() throws Exception {
        // 용도가 섞이지 않는다 (REQ-EV-005). SIGNUP은 이미 가입된 이메일이면 EV006이라 다른 이메일로 받는다.
        String otherEmail = "other-" + UUID.randomUUID().toString().substring(0, 8) + email.substring(email.indexOf('@'));
        String signupToken = issueToken(otherEmail, "SIGNUP");

        mockMvc.perform(resetRequest(signupToken, "latte5678"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EV008"));
    }

    // ---------- 헬퍼 ----------

    private String issuePasswordResetToken() throws Exception {
        return issueToken(email, "PASSWORD_RESET");
    }

    private String issueToken(String targetEmail, String purpose) throws Exception {
        mockMvc.perform(post("/api/v1/verifications/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", targetEmail, "purpose", purpose))))
                .andExpect(status().isOk());

        MvcResult confirm = mockMvc.perform(post("/api/v1/verifications/email/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", targetEmail, "purpose", purpose, "code", mailSender.lastCode))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(confirm.getResponse().getContentAsString())
                .path("data").path("verificationToken").asText();
    }

    private org.springframework.test.web.servlet.ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private MockHttpServletRequestBuilder resetRequest(String verificationToken, String newPassword)
            throws Exception {
        return post("/api/v1/members/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("verificationToken", verificationToken, "newPassword", newPassword)));
    }
}
