package com.coffeul.member;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.support.MailTestConfig;
import com.coffeul.support.RecordingMailSender;
import com.coffeul.support.TestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SM-3(회원가입) 엔드투엔드 검증 — REQ-U-001 · 002 · 003, REQ-EV-005.
 * 인증 토큰은 SM-1 → SM-2를 실제로 거쳐서 받는다 (가짜 메일 어댑터에서 코드를 꺼냄).
 */
@Import(MailTestConfig.class)
class MemberRegisterIntegrationTest extends AbstractIntegrationTest {

    private static final String TERMS_VERSION = "2026-10-01";

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

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        mailSender.reset();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        schoolId = fixtures.createSchool("테스트대학교-" + unique);
        String domain = "s" + unique + ".test.ac.kr";
        fixtures.createSchoolEmailDomain(schoolId, domain);
        email = "student@" + domain;
    }

    @Test
    void 가입하면_회원_약관_로그인_토큰이_한번에_만들어진다() throws Exception {
        String token = issueVerificationToken("SIGNUP");

        MvcResult result = mockMvc.perform(registerRequest(token, "김학생", "coffee1234", bothTermsAgreed()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("가입을 환영해요."))
                .andExpect(jsonPath("$.data.name").value("김학생"))
                // 이메일은 요청에 없다 — 인증 토큰에 묶인 것을 쓴다.
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.school.schoolId").value(schoolId))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        long memberId = data.path("memberId").asLong();
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).startsWith("rt_");

        assertThat(fixtures.countMembersByEmail(email)).isEqualTo(1);
        assertThat(fixtures.countTermsAgreements(memberId)).isEqualTo(2);
        assertThat(fixtures.countActiveRefreshTokensBySubject("MEMBER", memberId)).isEqualTo(1);
        // 인증 토큰은 한 번 쓰면 끝 (REQ-EV-005).
        assertThat(fixtures.consumedAtOf(email)).isNotNull();
    }

    @Test
    void 가입한_계정으로_바로_로그인된다() throws Exception {
        String token = issueVerificationToken("SIGNUP");
        mockMvc.perform(registerRequest(token, "김학생", "coffee1234", bothTermsAgreed()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "coffee1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member.name").value("김학생"));
    }

    @Test
    void 이름이_없으면_C001() throws Exception {
        String token = issueVerificationToken("SIGNUP");

        mockMvc.perform(registerRequest(token, "  ", "coffee1234", bothTermsAgreed()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));

        assertThat(fixtures.countMembersByEmail(email)).isZero();
    }

    @Test
    void 비밀번호가_규칙에_맞지_않으면_U002() throws Exception {
        String token = issueVerificationToken("SIGNUP");

        mockMvc.perform(registerRequest(token, "김학생", "coffee", bothTermsAgreed()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("U002"));

        assertThat(fixtures.countMembersByEmail(email)).isZero();
        // 규칙 위반으로 막혔으면 인증 토큰은 아직 살아 있어야 한다 — 다시 제출할 수 있어야 하니까.
        assertThat(fixtures.consumedAtOf(email)).isNull();
    }

    @Test
    void 필수_약관에_동의하지_않으면_U003() throws Exception {
        String token = issueVerificationToken("SIGNUP");
        List<Map<String, Object>> onlyService = List.of(agreement("SERVICE", true), agreement("PRIVACY", false));

        mockMvc.perform(registerRequest(token, "김학생", "coffee1234", onlyService))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("U003"));

        assertThat(fixtures.countMembersByEmail(email)).isZero();
        assertThat(fixtures.consumedAtOf(email)).isNull();
    }

    @Test
    void 인증_토큰이_이미_쓰였으면_EV008() throws Exception {
        String token = issueVerificationToken("SIGNUP");
        mockMvc.perform(registerRequest(token, "김학생", "coffee1234", bothTermsAgreed()))
                .andExpect(status().isCreated());

        mockMvc.perform(registerRequest(token, "다른학생", "coffee1234", bothTermsAgreed()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EV008"));

        assertThat(fixtures.countMembersByEmail(email)).isEqualTo(1);
    }

    @Test
    void 없는_인증_토큰이면_EV008() throws Exception {
        mockMvc.perform(registerRequest("evt_존재하지않는토큰", "김학생", "coffee1234", bothTermsAgreed()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EV008"));
    }

    @Test
    void 비밀번호_재설정용_토큰으로는_가입할_수_없다() throws Exception {
        // 용도가 섞이지 않는다 (REQ-EV-005). 재설정 토큰을 받으려면 이미 가입돼 있어야 해서 회원을 먼저 만든다.
        fixtures.createMemberWithPassword(schoolId, email, "coffee1234", "ACTIVE");
        String resetToken = issueVerificationToken("PASSWORD_RESET");

        mockMvc.perform(registerRequest(resetToken, "김학생", "coffee1234", bothTermsAgreed()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EV008"));
    }

    @Test
    void 인증_토큰을_받은_뒤_같은_이메일로_먼저_가입되면_U001() throws Exception {
        String token = issueVerificationToken("SIGNUP");
        // 토큰을 들고 있는 사이에 다른 경로로 같은 이메일이 가입된 상황.
        fixtures.createMember(schoolId, email, "ACTIVE");

        mockMvc.perform(registerRequest(token, "김학생", "coffee1234", bothTermsAgreed()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("U001"));

        assertThat(fixtures.countMembersByEmail(email)).isEqualTo(1);
    }

    @Test
    void 약관_종류가_정해진_값이_아니면_C001() throws Exception {
        String token = issueVerificationToken("SIGNUP");
        List<Map<String, Object>> weird = List.of(agreement("MARKETING", true), agreement("PRIVACY", true));

        mockMvc.perform(registerRequest(token, "김학생", "coffee1234", weird))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    // ---------- 헬퍼 ----------

    /** SM-1 → SM-2를 실제로 거쳐 인증 토큰을 받는다. */
    private String issueVerificationToken(String purpose) throws Exception {
        mockMvc.perform(post("/api/v1/verifications/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "purpose", purpose))))
                .andExpect(status().isOk());

        MvcResult confirm = mockMvc.perform(post("/api/v1/verifications/email/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "purpose", purpose, "code", mailSender.lastCode))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(confirm.getResponse().getContentAsString())
                .path("data").path("verificationToken").asText();
    }

    private List<Map<String, Object>> bothTermsAgreed() {
        return List.of(agreement("SERVICE", true), agreement("PRIVACY", true));
    }

    private Map<String, Object> agreement(String type, boolean agreed) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("version", TERMS_VERSION);
        map.put("agreed", agreed);
        return map;
    }

    private MockHttpServletRequestBuilder registerRequest(String verificationToken, String name, String password,
                                                           List<Map<String, Object>> agreements) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("verificationToken", verificationToken);
        body.put("name", name);
        body.put("password", password);
        body.put("agreements", agreements);
        return post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }
}
