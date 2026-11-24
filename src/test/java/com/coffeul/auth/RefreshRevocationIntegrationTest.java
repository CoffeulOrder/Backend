package com.coffeul.auth;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.api.TokenIssuer;
import com.coffeul.auth.infrastructure.TokenHasher;
import com.coffeul.support.MailTestConfig;
import com.coffeul.support.RecordingMailSender;
import com.coffeul.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(MailTestConfig.class)
class RefreshRevocationIntegrationTest extends AbstractIntegrationTest {
    private static final String OLD_PASSWORD = "coffee1234";
    private static final String NEW_PASSWORD = "latte5678";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private RecordingMailSender mail;
    @MockitoSpyBean private TokenIssuer issuer;
    private TestFixtures fixtures;
    private long schoolId;
    private long memberId;
    private String email;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbc);
        String unique = UUID.randomUUID().toString();
        schoolId = fixtures.createSchool("세션 폐기-" + unique);
        String domain = unique + ".test.ac.kr";
        fixtures.createSchoolEmailDomain(schoolId, domain);
        email = "student@" + domain;
        memberId = fixtures.createMemberWithPassword(schoolId, email, OLD_PASSWORD, "ACTIVE");
        mail.reset();
    }

    @Test
    void logoutMustBlockImmediateRefresh() throws Exception {
        Session session = login();
        logout(session).andExpect(status().isOk());
        assertRejected(session);
    }

    @Test
    void logoutWithRotatedAncestorMustInvalidateCurrentSession() throws Exception {
        Session ancestor = login();
        Session current = rotated(ancestor);
        logout(ancestor).andExpect(status().isOk());
        assertRejected(ancestor);
        assertRejected(current);
    }

    @Test
    void passwordChangeMustAlsoExpireRotatedAncestors() throws Exception {
        Session ancestor = login();
        Session current = rotated(ancestor);
        mvc.perform(patch("/api/v1/members/me/password").header("Authorization", "Bearer " + current.accessToken())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                        Map.of("currentPassword", OLD_PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk());
        assertRejected(ancestor);
        assertRejected(current);
    }

    @Test
    void passwordResetMustAlsoExpireRotatedAncestors() throws Exception {
        Session ancestor = login();
        Session current = rotated(ancestor);
        String token = resetToken();
        mvc.perform(post("/api/v1/members/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("verificationToken", token, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk());
        assertRejected(ancestor);
        assertRejected(current);
    }

    @Test
    void logoutOnlyInvalidatesItsOwnDeviceFamily() throws Exception {
        Session first = login();
        Session firstRotated = rotated(first);
        Session secondDevice = login();
        logout(firstRotated).andExpect(status().isOk());
        logout(first).andExpect(status().isOk());
        assertRejected(first);
        assertRejected(firstRotated);
        rotated(secondDevice);
    }

    @Test
    void normalRotationStillAllowsImmediateRetryWithinTheGracePeriod() throws Exception {
        Session first = login();
        Session successor = rotated(first);
        Session retry = rotated(first);
        rotated(successor);
        rotated(retry);
    }

    @Test
    void passwordChangeInvalidatesAllDevicesAndMultipleAncestors() throws Exception {
        Session first = login();
        Session next = rotated(first);
        Session last = rotated(next);
        Session otherDevice = login();
        invalidate("CHANGE", last, null).andExpect(status().isOk());
        for (Session session : java.util.List.of(first, next, last, otherDevice)) assertRejected(session);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "STAFF"})
    void staffLogoutExpiresTheWholeRotationFamily(String role) throws Exception {
        String unique = UUID.randomUUID().toString();
        long merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & Integer.MAX_VALUE), new BigDecimal("0.0300"));
        long staffId = fixtures.createStaffAccount(merchantId, role, unique, OLD_PASSWORD, "직원", "ACTIVE");
        long storeId = fixtures.createStore(merchantId, schoolId, "직원 테스트 매장", "OPEN");
        if ("STAFF".equals(role)) fixtures.createStaffStore(staffId, storeId);
        String response = mvc.perform(post("/api/v1/auth/staff/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("loginId", unique, "password", OLD_PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Session first = session(response);
        Session successor = rotated(first);
        logout(successor).andExpect(status().isOk());
        assertRejected(first);
        assertRejected(successor);
    }

    @Test
    void lateReuseInvalidatesItsFamilyWithoutLoggingOutAnotherDevice() throws Exception {
        Session first = login();
        Session successor = rotated(first);
        Session otherDevice = login();
        fixtures.ageRefreshTokenRevocation(TokenHasher.sha256Hex(first.refreshToken()), Instant.now().minusSeconds(31));
        assertRejected(first);
        assertRejected(successor);
        rotated(otherDevice);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOGOUT", "CHANGE", "RESET"})
    void explicitInvalidationWaitsForAnInFlightRotationAndExpiresItsResult(String action) throws Exception {
        Session first = login();
        String token = "RESET".equals(action) ? resetToken() : null;
        CountDownLatch rotating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch invalidating = new CountDownLatch(1);
        TokenIssuer target = AopTestUtils.getUltimateTargetObject(issuer);
        doAnswer(call -> {
            rotating.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test rotation was not released");
            return call.callRealMethod();
        }).when(target).rotate(any(AuthUser.class), anyString());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var rotation = pool.submit(() -> rotated(first));
            assertThat(rotating.await(5, TimeUnit.SECONDS)).isTrue();
            var invalidation = pool.submit(() -> {
                invalidating.countDown();
                return invalidate(action, first, token).andReturn().getResponse().getStatus();
            });
            try {
                assertThat(invalidating.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> invalidation.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            Session renewed = rotation.get(10, TimeUnit.SECONDS);
            assertThat(invalidation.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertRejected(first);
            assertRejected(renewed);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE subject_type='MEMBER' "
                    + "AND subject_id=? AND revoked_at IS NULL", Integer.class, memberId)).isZero();
        } finally {
            release.countDown();
        }
    }

    private ResultActions invalidate(String action, Session current, String token) throws Exception {
        return switch (action) {
            case "LOGOUT" -> logout(current);
            case "CHANGE" -> mvc.perform(patch("/api/v1/members/me/password")
                    .header("Authorization", "Bearer " + current.accessToken()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("currentPassword", OLD_PASSWORD, "newPassword", NEW_PASSWORD))));
            case "RESET" -> mvc.perform(post("/api/v1/members/password-reset").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("verificationToken", token, "newPassword", NEW_PASSWORD))));
            default -> throw new IllegalArgumentException("Unknown test invalidation");
        };
    }

    private String resetToken() throws Exception {
        mvc.perform(post("/api/v1/verifications/email").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "purpose", "PASSWORD_RESET"))))
                .andExpect(status().isOk());
        String response = mvc.perform(post("/api/v1/verifications/email/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "purpose", "PASSWORD_RESET", "code", mail.lastCode))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/verificationToken").asText();
    }

    private Session login() throws Exception {
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", OLD_PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return session(response);
    }

    private Session rotated(Session old) throws Exception {
        return session(refresh(old).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private Session session(String response) throws Exception {
        var data = mapper.readTree(response).path("data");
        return new Session(data.path("accessToken").asText(), data.path("refreshToken").asText());
    }

    private ResultActions refresh(Session session) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", session.refreshToken()))));
    }

    private ResultActions logout(Session session) throws Exception {
        return mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + session.accessToken())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("refreshToken", session.refreshToken()))));
    }

    private void assertRejected(Session session) throws Exception {
        refresh(session).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    private record Session(String accessToken, String refreshToken) { }
}
