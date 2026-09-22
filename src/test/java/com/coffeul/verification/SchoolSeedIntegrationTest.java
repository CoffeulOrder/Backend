package com.coffeul.verification;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.support.MailTestConfig;
import com.coffeul.support.RecordingMailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2 시드(을지대학교 성남캠퍼스 · g.eulji.ac.kr)가 실제로 들어갔는지.
 * <p>이게 없으면 REQ-EV-001이 모든 학교 이메일을 EV001로 막아서 서버가 회원을 하나도 못 받는다 —
 * 테스트는 각자 도메인을 심어서 통과하므로, 진짜 학교 도메인은 여기서만 확인된다.
 */
@Import(MailTestConfig.class)
class SchoolSeedIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RecordingMailSender mailSender;

    @Test
    void 을지대_성남캠퍼스와_학교_이메일_도메인이_들어있다() {
        Long schoolId = jdbcTemplate.queryForObject(
                "SELECT `id` FROM `school` WHERE `name` = ? AND `campus` = ?",
                Long.class, "을지대학교", "성남캠퍼스");
        assertThat(schoolId).isNotNull();

        Long linkedSchoolId = jdbcTemplate.queryForObject(
                "SELECT `school_id` FROM `school_email_domain` WHERE `domain` = ?",
                Long.class, "g.eulji.ac.kr");
        assertThat(linkedSchoolId).isEqualTo(schoolId);
    }

    @Test
    void 실제_학교_이메일로_인증코드를_받을_수_있다() throws Exception {
        mailSender.reset();
        String email = "test-" + UUID.randomUUID().toString().substring(0, 8) + "@g.eulji.ac.kr";

        mockMvc.perform(post("/api/v1/verifications/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "purpose", "SIGNUP"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));

        assertThat(mailSender.lastCode).matches("\\d{6}");
    }
}
