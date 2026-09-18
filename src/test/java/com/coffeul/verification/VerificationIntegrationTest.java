package com.coffeul.verification;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.support.TestFixtures;
import com.coffeul.verification.application.VerificationCleanupJob;
import com.coffeul.verification.application.VerificationMailSender;
import com.coffeul.verification.domain.VerificationPurpose;
import com.coffeul.verification.infrastructure.VerificationHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

/**
 * SM-1 · SM-2(인증코드 발송 · 확인) 엔드투엔드 검증.
 * rules.py 완료 기준 — "명세의 ❌ 실패 목록마다 테스트가 1개 이상 있다"에 맞춰 실패 케이스를 전부 덮는다.
 */
class VerificationIntegrationTest extends AbstractIntegrationTest {

    /** 메일은 포트 뒤에 있으므로 테스트는 가짜 어댑터로 돈다 (rules.py DOD). 코드 평문을 여기서만 꺼낸다. */
    static class RecordingMailSender implements VerificationMailSender {

        String lastEmail;
        String lastCode;
        VerificationPurpose lastPurpose;
        int sendCount;
        boolean failNext;

        @Override
        public void send(String email, VerificationPurpose purpose, String code) {
            if (failNext) {
                throw new MailDeliveryException("테스트용 발송 실패", null);
            }
            this.lastEmail = email;
            this.lastPurpose = purpose;
            this.lastCode = code;
            this.sendCount++;
        }

        void reset() {
            lastEmail = null;
            lastCode = null;
            lastPurpose = null;
            sendCount = 0;
            failNext = false;
        }
    }

    @TestConfiguration
    static class MailTestConfig {

        @Bean
        @Primary
        RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RecordingMailSender mailSender;
    @Autowired
    private VerificationCleanupJob cleanupJob;

    private TestFixtures fixtures;
    private long schoolId;
    private String email;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        mailSender.reset();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        schoolId = fixtures.createSchool("테스트대학교-" + unique);
        // school_email_domain.domain이 UNIQUE라 테스트마다 다른 도메인을 쓴다.
        String domain = "s" + unique + ".test.ac.kr";
        fixtures.createSchoolEmailDomain(schoolId, domain);
        email = "student@" + domain;
    }

    // ---------- SM-1 인증코드 발송 ----------

    @Test
    void 인증코드를_보내면_코드_행이_생기고_메일이_나간다() throws Exception {
        mockMvc.perform(sendRequest(email, "SIGNUP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.resendAvailableInSeconds").value(60));

        assertThat(fixtures.countEmailVerifications(email)).isEqualTo(1);
        assertThat(mailSender.sendCount).isEqualTo(1);
        assertThat(mailSender.lastEmail).isEqualTo(email);
        assertThat(mailSender.lastPurpose).isEqualTo(VerificationPurpose.SIGNUP);
        assertThat(mailSender.lastCode).matches("\\d{6}");
    }

    @Test
    void 이메일_형식이_틀리면_C001() throws Exception {
        mockMvc.perform(sendRequest("not-an-email", "SIGNUP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void 등록되지_않은_학교_도메인이면_EV001() throws Exception {
        mockMvc.perform(sendRequest("student@gmail.com", "SIGNUP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EV001"));

        assertThat(mailSender.sendCount).isZero();
    }

    @Test
    void 요청_60초_안에_다시_요청하면_EV002() throws Exception {
        mockMvc.perform(sendRequest(email, "SIGNUP")).andExpect(status().isOk());

        mockMvc.perform(sendRequest(email, "SIGNUP"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EV002"))
                .andExpect(jsonPath("$.data.retryAfterSeconds").isNumber());

        assertThat(fixtures.countEmailVerifications(email)).isEqualTo(1);
    }

    @Test
    void 하루_10회를_넘기면_EV003() throws Exception {
        // 60초 재발송 제한에 먼저 걸리지 않게 조금 지난 것으로 심되, 자정을 넘어가지 않게 가까이 둔다
        // ("하루 10회"는 Asia/Seoul 자정 기준이라 너무 과거로 심으면 어제 것이 된다).
        Instant createdAt = Instant.now().minus(70, ChronoUnit.SECONDS);
        for (int i = 0; i < 10; i++) {
            fixtures.insertEmailVerification(email, "SIGNUP", VerificationHasher.sha256Hex("00000" + i),
                    createdAt.plus(5, ChronoUnit.MINUTES), createdAt);
        }

        mockMvc.perform(sendRequest(email, "SIGNUP"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EV003"));

        assertThat(fixtures.countEmailVerifications(email)).isEqualTo(10);
    }

    @Test
    void 이미_가입된_이메일로_가입_인증을_요청하면_EV006() throws Exception {
        fixtures.createMember(schoolId, email, "ACTIVE");

        mockMvc.perform(sendRequest(email, "SIGNUP"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EV006"));

        assertThat(fixtures.countEmailVerifications(email)).isZero();
        assertThat(mailSender.sendCount).isZero();
    }

    @Test
    void 메일_발송에_실패하면_EV007이고_코드는_저장되지_않는다() throws Exception {
        mailSender.failNext = true;

        mockMvc.perform(sendRequest(email, "SIGNUP"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EV007"));

        assertThat(fixtures.countEmailVerifications(email)).isZero();
    }

    @Test
    void 가입되지_않은_이메일의_비밀번호_재설정은_200이지만_메일은_나가지_않는다() throws Exception {
        mockMvc.perform(sendRequest(email, "PASSWORD_RESET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        assertThat(mailSender.sendCount).isZero();
        // 행은 남긴다 — 가입된 이메일만 재발송 제한에 걸리면 그 차이로 가입 여부가 드러난다.
        assertThat(fixtures.countEmailVerifications(email)).isEqualTo(1);
    }

    @Test
    void purpose가_정해진_값이_아니면_C001() throws Exception {
        mockMvc.perform(sendRequest(email, "LOGIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("purpose"));
    }

    // ---------- SM-2 인증코드 확인 ----------

    @Test
    void 코드가_맞으면_인증_토큰을_준다() throws Exception {
        String code = sendAndCaptureCode("SIGNUP");

        MvcResult result = mockMvc.perform(confirmRequest(email, "SIGNUP", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(1800))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        String token = data.path("verificationToken").asText();
        assertThat(token).startsWith("evt_");
        // 토큰은 평문으로 저장되지 않는다 — DB엔 SHA-256만.
        assertThat(fixtures.tokenHashOf(email)).isEqualTo(VerificationHasher.sha256Hex(token));
    }

    @Test
    void 코드가_틀리면_EV004와_남은_횟수를_주고_실패가_기록된다() throws Exception {
        sendAndCaptureCode("SIGNUP");

        mockMvc.perform(confirmRequest(email, "SIGNUP", "000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EV004"))
                .andExpect(jsonPath("$.data.remainingAttempts").value(4));

        // 실패 횟수가 예외와 함께 롤백되면 5회 폐기가 영원히 작동하지 않는다.
        assertThat(fixtures.attemptCountOf(email)).isEqualTo(1);
    }

    @Test
    void 코드를_5회_틀리면_폐기돼서_정답도_EV005() throws Exception {
        String code = sendAndCaptureCode("SIGNUP");

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(confirmRequest(email, "SIGNUP", "000000"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EV004"));
        }
        mockMvc.perform(confirmRequest(email, "SIGNUP", "000000"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EV005"));

        assertThat(fixtures.attemptCountOf(email)).isEqualTo(5);
        mockMvc.perform(confirmRequest(email, "SIGNUP", code))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EV005"));
    }

    @Test
    void 요청_이력이_없으면_EV005() throws Exception {
        mockMvc.perform(confirmRequest(email, "SIGNUP", "123456"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EV005"));
    }

    @Test
    void 만료된_코드는_EV005() throws Exception {
        String code = sendAndCaptureCode("SIGNUP");
        Instant past = Instant.now().minus(10, ChronoUnit.MINUTES);
        fixtures.ageEmailVerification(email, past, past.plus(5, ChronoUnit.MINUTES));

        mockMvc.perform(confirmRequest(email, "SIGNUP", code))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EV005"));
    }

    @Test
    void 가입용_코드는_비밀번호_재설정에_쓸_수_없다() throws Exception {
        String code = sendAndCaptureCode("SIGNUP");

        mockMvc.perform(confirmRequest(email, "PASSWORD_RESET", code))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EV005"));
    }

    // ---------- 스케줄 작업 ----------

    @Test
    void 정리_작업은_만료_후_7일_지난_코드만_지운다() {
        Instant now = Instant.now();
        Instant longAgo = now.minus(30, ChronoUnit.DAYS);
        fixtures.insertEmailVerification(email, "SIGNUP", VerificationHasher.sha256Hex("111111"),
                longAgo.plus(5, ChronoUnit.MINUTES), longAgo);
        fixtures.insertEmailVerification(email, "SIGNUP", VerificationHasher.sha256Hex("222222"),
                now.plus(5, ChronoUnit.MINUTES), now);

        cleanupJob.deleteExpired(now);

        assertThat(fixtures.countEmailVerifications(email)).isEqualTo(1);
    }

    // ---------- 헬퍼 ----------

    private String sendAndCaptureCode(String purpose) throws Exception {
        mockMvc.perform(sendRequest(email, purpose)).andExpect(status().isOk());
        return mailSender.lastCode;
    }

    private MockHttpServletRequestBuilder sendRequest(String targetEmail, String purpose) throws Exception {
        return post("/api/v1/verifications/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", targetEmail, "purpose", purpose)));
    }

    private MockHttpServletRequestBuilder confirmRequest(String targetEmail, String purpose, String code)
            throws Exception {
        return post("/api/v1/verifications/email/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("email", targetEmail, "purpose", purpose, "code", code)));
    }
}
