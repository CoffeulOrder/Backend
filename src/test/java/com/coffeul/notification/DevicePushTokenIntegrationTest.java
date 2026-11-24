package com.coffeul.notification;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TW-1(푸시 토큰 등록 · 갱신) 엔드투엔드 — 실제 MySQL의 upsert와 ck_device_token_app_owner까지 확인한다. */
class DevicePushTokenIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/devices/push-token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtAccessTokenService jwtAccessTokenService;

    private TestFixtures fixtures;
    private long schoolId;
    private long merchantId;
    private long storeId;
    private long memberId;
    private long staffId;
    private String customerAuth;
    private String staffAuth;
    private String ownerAuth;
    private String token;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        schoolId = fixtures.createSchool("테스트대학교-" + unique);
        merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & 0x7fffffff), new BigDecimal("0.0300"));
        storeId = fixtures.createStore(merchantId, schoolId, "테스트매장", "OPEN");
        memberId = fixtures.createMember(schoolId, "member-" + unique + "@test.ac.kr", "ACTIVE");
        staffId = fixtures.createStaffAccount(merchantId, "STAFF", "staff-" + unique, "pw1234!!", "테스트직원", "ACTIVE");
        fixtures.createStaffStore(staffId, storeId);
        long ownerId = fixtures.createStaffAccount(merchantId, "OWNER", "owner-" + unique, "pw1234!!", "테스트사장", "ACTIVE");

        customerAuth = authHeaderFor(new AuthUser(memberId, AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, schoolId, null));
        staffAuth = authHeaderFor(new AuthUser(staffId, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, merchantId));
        ownerAuth = authHeaderFor(new AuthUser(ownerId, AuthUser.SubjectType.STAFF, AuthUser.Role.OWNER, null, merchantId));
        token = "ExponentPushToken[" + UUID.randomUUID() + "]";
    }

    private String authHeaderFor(AuthUser user) {
        return "Bearer " + jwtAccessTokenService.encode(user, Instant.now());
    }

    private String body(String expoPushToken, String platform, String appType, Long storeId) {
        return "{\"expoPushToken\":" + quote(expoPushToken) + ",\"platform\":" + quote(platform)
                + ",\"appType\":" + quote(appType) + ",\"storeId\":" + storeId + "}";
    }

    private String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private ResultActions register(String auth, String body) throws Exception {
        var request = put(URL).contentType(MediaType.APPLICATION_JSON).content(body);
        if (auth != null) {
            request = request.header("Authorization", auth);
        }
        return mockMvc.perform(request);
    }

    private List<Map<String, Object>> rowsOf(String expoPushToken) {
        return jdbcTemplate.queryForList("SELECT * FROM `device_token` WHERE `expo_push_token` = ?", expoPushToken);
    }

    @Test
    void 고객앱_토큰을_등록한다() throws Exception {
        register(customerAuth, body(token, "IOS", "CUSTOMER", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("알림을 받을 준비가 됐어요."))
                .andExpect(jsonPath("$.data.deviceTokenId").isNumber())
                .andExpect(jsonPath("$.data.active").value(true));

        List<Map<String, Object>> rows = rowsOf(token);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("owner_type", "MEMBER")
                .containsEntry("owner_id", memberId)
                .containsEntry("app_type", "CUSTOMER")
                .containsEntry("platform", "IOS")
                .containsEntry("store_id", null);
    }

    @Test
    void 관리자앱_토큰은_매장과_함께_등록한다() throws Exception {
        register(staffAuth, body(token, "ANDROID", "MANAGER", storeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        Map<String, Object> row = rowsOf(token).get(0);
        assertThat(row).containsEntry("owner_type", "STAFF")
                .containsEntry("owner_id", staffId)
                .containsEntry("app_type", "MANAGER")
                .containsEntry("store_id", storeId)
                .containsEntry("platform", "ANDROID");
    }

    @Test
    void 사장님도_자기_매장으로_등록할_수_있다() throws Exception {
        register(ownerAuth, body(token, "IOS", "MANAGER", storeId)).andExpect(status().isOk());
    }

    @Test
    void 같은_요청을_두_번_보내도_한_행이고_id가_같다() throws Exception {
        register(customerAuth, body(token, "IOS", "CUSTOMER", null)).andExpect(status().isOk());
        Long firstId = (Long) rowsOf(token).get(0).get("id");

        register(customerAuth, body(token, "IOS", "CUSTOMER", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceTokenId").value(firstId));

        assertThat(rowsOf(token)).hasSize(1);
    }

    @Test
    void 같은_토큰이_다른_계정으로_오면_주인이_바뀐다() throws Exception {
        register(customerAuth, body(token, "IOS", "CUSTOMER", null)).andExpect(status().isOk());

        long otherMemberId = fixtures.createMember(schoolId, "other-" + UUID.randomUUID() + "@test.ac.kr", "ACTIVE");
        String otherAuth = authHeaderFor(new AuthUser(otherMemberId, AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, schoolId, null));
        register(otherAuth, body(token, "IOS", "CUSTOMER", null)).andExpect(status().isOk());

        List<Map<String, Object>> rows = rowsOf(token);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("owner_id", otherMemberId);
    }

    @Test
    void 같은_기기에서_관리자앱_계정으로_바꿔도_제약을_지키며_주인이_바뀐다() throws Exception {
        register(staffAuth, body(token, "IOS", "MANAGER", storeId)).andExpect(status().isOk());
        register(customerAuth, body(token, "IOS", "CUSTOMER", null)).andExpect(status().isOk());

        Map<String, Object> row = rowsOf(token).get(0);
        assertThat(row).containsEntry("owner_type", "MEMBER")
                .containsEntry("owner_id", memberId)
                .containsEntry("app_type", "CUSTOMER")
                .containsEntry("store_id", null);
    }

    @Test
    void 관리자앱이_매장을_바꾸면_store_id가_갱신된다() throws Exception {
        long secondStoreId = fixtures.createStore(merchantId, schoolId, "두번째매장", "OPEN");

        register(ownerAuth, body(token, "IOS", "MANAGER", storeId)).andExpect(status().isOk());
        register(ownerAuth, body(token, "IOS", "MANAGER", secondStoreId)).andExpect(status().isOk());

        List<Map<String, Object>> rows = rowsOf(token);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("store_id", secondStoreId);
    }

    @Test
    void 비활성이던_토큰을_다시_등록하면_살아난다() throws Exception {
        register(customerAuth, body(token, "IOS", "CUSTOMER", null)).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE `device_token` SET `is_active` = FALSE, `deactivated_at` = ? WHERE `expo_push_token` = ?",
                Instant.now(), token);

        register(customerAuth, body(token, "IOS", "CUSTOMER", null)).andExpect(status().isOk());

        Map<String, Object> row = rowsOf(token).get(0);
        assertThat(row).containsEntry("is_active", true).containsEntry("deactivated_at", null);
    }

    @Test
    void 토큰_형식이_틀리면_400_NT001이다() throws Exception {
        register(customerAuth, body("fcm-not-expo", "IOS", "CUSTOMER", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NT001"))
                .andExpect(jsonPath("$.message").value("푸시 토큰 형식이 올바르지 않아요."));
    }

    @Test
    void 관리자앱인데_storeId가_없으면_400_C001이다() throws Exception {
        register(staffAuth, body(token, "IOS", "MANAGER", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.message").value("매장을 선택해주세요."))
                .andExpect(jsonPath("$.errors[0].field").value("storeId"));

        assertThat(rowsOf(token)).isEmpty();
    }

    @Test
    void 고객앱인데_storeId가_있으면_400_C001이다() throws Exception {
        register(customerAuth, body(token, "IOS", "CUSTOMER", storeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("storeId"));
    }

    @Test
    void platform이나_appType이_허용값이_아니면_400_C001이다() throws Exception {
        register(customerAuth, body(token, "WINDOWS", "CUSTOMER", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("platform"));

        register(customerAuth, body(token, "IOS", "TABLET", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("appType"));
    }

    @Test
    void 필수값이_빠지면_400_C001이다() throws Exception {
        register(customerAuth, body(null, "IOS", "CUSTOMER", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.errors[0].field").value("expoPushToken"));
    }

    @Test
    void 고객_토큰으로_MANAGER를_등록하면_403_C003이다() throws Exception {
        register(customerAuth, body(token, "IOS", "MANAGER", storeId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C003"));

        assertThat(rowsOf(token)).isEmpty();
    }

    @Test
    void 직원_토큰으로_CUSTOMER를_등록하면_403_C003이다() throws Exception {
        register(staffAuth, body(token, "IOS", "CUSTOMER", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    @Test
    void ADMIN은_등록할_수_없다() throws Exception {
        String adminAuth = authHeaderFor(new AuthUser(1L, AuthUser.SubjectType.STAFF, AuthUser.Role.ADMIN, null, null));

        register(adminAuth, body(token, "IOS", "MANAGER", storeId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    @Test
    void 다른_사장님_매장이면_403_AUTH_004이다() throws Exception {
        long otherMerchantId = fixtures.createMerchant(
                String.format("%010d", UUID.randomUUID().toString().hashCode() & 0x7fffffff), new BigDecimal("0.0300"));
        long otherStoreId = fixtures.createStore(otherMerchantId, schoolId, "남의매장", "OPEN");

        register(staffAuth, body(token, "IOS", "MANAGER", otherStoreId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_004"));

        register(ownerAuth, body(token, "IOS", "MANAGER", otherStoreId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_004"));

        assertThat(rowsOf(token)).isEmpty();
    }

    @Test
    void 토큰이_없으면_401_C002이다() throws Exception {
        register(null, body(token, "IOS", "CUSTOMER", null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("C002"));
    }
}
