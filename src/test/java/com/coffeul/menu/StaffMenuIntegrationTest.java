package com.coffeul.menu;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.support.TestFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@TestPropertySource(properties = {"coffeul.payment.gateway=fake", "coffeul.dev.payment-flow-enabled=true"})
class StaffMenuIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtAccessTokenService tokens;
    private TestFixtures fixtures;
    private long schoolId;
    private long merchantId;
    private long storeId;
    private long otherStoreId;
    private long ownerId;
    private long staffId;
    private long menuId;
    private long groupId;
    private long optionId;
    private long otherMenuId;
    private long otherOptionId;
    private String owner;
    private String staff;
    private String customer;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbc);
        String unique = UUID.randomUUID().toString();
        schoolId = fixtures.createSchool("품절 관리-" + unique);
        merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & Integer.MAX_VALUE), new BigDecimal("0.0300"));
        jdbc.update("INSERT INTO merchant_pg (merchant_id,pg_provider,client_key,secret_ref) "
                + "VALUES (?,'FAKE','synthetic-test-client','fake://synthetic-test')", merchantId);
        storeId = fixtures.createStore(merchantId, schoolId, "품절 관리 매장", "OPEN");
        otherStoreId = fixtures.createStore(merchantId, schoolId, "다른 매장", "OPEN");
        menuId = fixtures.createMenuItem(storeId, "카페라떼", 2500, false);
        groupId = fixtures.createOptionGroup(menuId, "사이즈", "SIZE", true, 1, 1);
        optionId = fixtures.createOptionItem(groupId, "R", 0, true);
        otherMenuId = fixtures.createMenuItem(otherStoreId, "다른 메뉴", 3000, false);
        long otherGroup = fixtures.createOptionGroup(otherMenuId, "사이즈", "SIZE", true, 1, 1);
        otherOptionId = fixtures.createOptionItem(otherGroup, "L", 500, false);
        ownerId = fixtures.createStaffAccount(merchantId, "OWNER", "owner-" + unique, "test-password", "사장님", "ACTIVE");
        staffId = fixtures.createStaffAccount(merchantId, "STAFF", "staff-" + unique, "test-password", "직원", "ACTIVE");
        fixtures.createStaffStore(staffId, storeId);
        owner = authorization(new AuthUser(ownerId, AuthUser.SubjectType.STAFF, AuthUser.Role.OWNER, null, merchantId));
        staff = authorization(new AuthUser(staffId, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, merchantId));
        long memberId = fixtures.createMember(schoolId, unique + "@test.ac.kr", "ACTIVE");
        customer = authorization(new AuthUser(memberId, AuthUser.SubjectType.MEMBER, AuthUser.Role.CUSTOMER, schoolId, null));
    }

    @Test
    void managementListIncludesHiddenMenusAndScopedSortedOptions() throws Exception {
        long hidden = fixtures.createMenuItem(storeId, "숨김 메뉴", 2000, true);
        long tied = fixtures.createMenuItem(storeId, "다음 메뉴", 2000, false);
        jdbc.update("UPDATE menu_item SET sort_order=10 WHERE id IN (?,?)", menuId, tied);
        jdbc.update("UPDATE menu_item SET status='HIDDEN',sort_order=1 WHERE id=?", hidden);
        jdbc.update("UPDATE option_group SET sort_order=10 WHERE id=?", groupId);
        long firstGroup = fixtures.createOptionGroup(menuId, "온도", "TEMPERATURE", true, 1, 1);
        long firstTemperature = fixtures.createOptionItem(firstGroup, "HOT", 0, true);
        long secondTemperature = fixtures.createOptionItem(firstGroup, "ICE", 0, false);
        long firstSize = fixtures.createOptionItem(groupId, "L", 500, false);
        jdbc.update("UPDATE option_item SET sort_order=10 WHERE id=?", optionId);
        jdbc.update("UPDATE option_item SET is_sold_out=TRUE WHERE id=?", firstSize);
        for (String auth : List.of(owner, staff)) {
            invoke("list", storeId, 0, auth, null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andExpect(jsonPath("$.data[0].menuId").value(hidden))
                    .andExpect(jsonPath("$.data[0].status").value("HIDDEN"))
                    .andExpect(jsonPath("$.data[0].soldOut").value(true))
                    .andExpect(jsonPath("$.data[0].optionGroups").isEmpty())
                    .andExpect(jsonPath("$.data[1].menuId").value(menuId))
                    .andExpect(jsonPath("$.data[2].menuId").value(tied))
                    .andExpect(jsonPath("$.data[1].optionGroups[0].optionGroupId").value(firstGroup))
                    .andExpect(jsonPath("$.data[1].optionGroups[0].options[0].optionItemId").value(firstTemperature))
                    .andExpect(jsonPath("$.data[1].optionGroups[0].options[1].optionItemId").value(secondTemperature))
                    .andExpect(jsonPath("$.data[1].optionGroups[1].optionGroupId").value(groupId))
                    .andExpect(jsonPath("$.data[1].optionGroups[1].options[0].optionItemId").value(firstSize))
                    .andExpect(jsonPath("$.data[1].optionGroups[1].options[0].soldOut").value(true))
                    .andExpect(jsonPath("$.data[1].optionGroups[1].options[1].optionItemId").value(optionId));
        }
    }

    @Test
    void ownedEmptyStoreReturnsAnEmptyList() throws Exception {
        long emptyStore = fixtures.createStore(merchantId, schoolId, "빈 매장", "CLOSED");
        invoke("list", emptyStore, 0, owner, null).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"menu", "option"})
    void ownerAndAssignedStaffCanSetAndClearSoldOutIdempotently(String action) throws Exception {
        for (boolean soldOut : List.of(true, true, false, false)) {
            invoke(action, storeId, target(action), soldOut ? staff : owner, "{\"soldOut\":" + soldOut + "}")
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.soldOut").value(soldOut));
            assertThat(isSoldOut(action)).isEqualTo(soldOut);
        }
        assertThat(jdbc.queryForObject("SELECT base_price FROM menu_item WHERE id=?", Integer.class, menuId)).isEqualTo(2500);
        assertThat(jdbc.queryForObject("SELECT name FROM menu_item WHERE id=?", String.class, menuId)).isEqualTo("카페라떼");
        assertThat(jdbc.queryForObject("SELECT status FROM menu_item WHERE id=?", String.class, menuId)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT option_group_id FROM option_item WHERE id=?", Long.class, optionId)).isEqualTo(groupId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "menu", "option"})
    void requestsRequireAnAuthenticatedStaffOrOwner(String action) throws Exception {
        invoke(action, storeId, target(action), null, "{\"soldOut\":true}").andExpect(status().isUnauthorized());
        String admin = authorization(new AuthUser(ownerId, AuthUser.SubjectType.STAFF, AuthUser.Role.ADMIN, null, null));
        String wrongSubject = authorization(new AuthUser(ownerId, AuthUser.SubjectType.MEMBER, AuthUser.Role.OWNER, null, merchantId));
        for (String auth : List.of(customer, admin, wrongSubject)) {
            invoke(action, storeId, target(action), auth, "{\"soldOut\":true}")
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("C003"));
        }
        assertThat(isSoldOut("menu")).isFalse();
        assertThat(isSoldOut("option")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "menu", "option"})
    void otherMerchantOwnersAndUnassignedStaffCannotAccessStore(String action) throws Exception {
        invoke(action, otherStoreId, target(action), staff, "{\"soldOut\":true}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_004"));
        long otherMerchant = fixtures.createMerchant(String.format("%010d", otherStoreId), new BigDecimal("0.0300"));
        long outsiderId = fixtures.createStaffAccount(otherMerchant, "OWNER", "outsider-" + UUID.randomUUID(),
                "test-password", "다른 사장님", "ACTIVE");
        String outsider = authorization(new AuthUser(outsiderId, AuthUser.SubjectType.STAFF, AuthUser.Role.OWNER, null, otherMerchant));
        invoke(action, storeId, target(action), outsider, "{\"soldOut\":true}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_004"));
        assertThat(isSoldOut("menu")).isFalse();
        assertThat(isSoldOut("option")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "menu", "option"})
    void inactiveOwnersAndStaffCannotUsePreviouslyIssuedTokens(String action) throws Exception {
        jdbc.update("UPDATE staff_account SET status='INACTIVE' WHERE id IN (?,?)", ownerId, staffId);
        for (String auth : List.of(owner, staff)) {
            invoke(action, storeId, target(action), auth, "{\"soldOut\":true}")
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_004"));
        }
        assertThat(isSoldOut("menu")).isFalse();
        assertThat(isSoldOut("option")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "merchant"})
    void accountChangesInvalidateAnOldOwnerToken(String changed) throws Exception {
        if ("role".equals(changed)) jdbc.update("UPDATE staff_account SET role='STAFF' WHERE id=?", ownerId);
        else {
            long nextMerchant = fixtures.createMerchant(String.format("%010d", otherMenuId), new BigDecimal("0.0300"));
            jdbc.update("UPDATE staff_account SET merchant_id=? WHERE id=?", nextMerchant, ownerId);
        }
        invoke("menu", storeId, menuId, owner, "{\"soldOut\":true}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_004"));
        assertThat(isSoldOut("menu")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"menu", "option"})
    void missingAndOtherStoreItemsReturnTheSameNotFound(String action) throws Exception {
        long foreignId = "menu".equals(action) ? otherMenuId : otherOptionId;
        for (long id : List.of(Long.MAX_VALUE, foreignId)) {
            invoke(action, storeId, id, owner, "{\"soldOut\":true}")
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("C004"));
        }
        assertThat(jdbc.queryForObject("SELECT is_sold_out FROM menu_item WHERE id=?", Boolean.class, otherMenuId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT is_sold_out FROM option_item WHERE id=?", Boolean.class, otherOptionId)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "menu", "option"})
    void unknownStoreIsNotFound(String action) throws Exception {
        invoke(action, Long.MAX_VALUE, target(action), owner, "{\"soldOut\":true}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ST001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"menu", "option"})
    void missingNullAndMalformedSoldOutValuesAreRejected(String action) throws Exception {
        for (String body : List.of("", "{}", "{\"soldOut\":null}", "{\"soldOut\":\"invalid\"}", "null", "{")) {
            invoke(action, storeId, target(action), owner, body)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("C001"));
        }
        assertThat(isSoldOut(action)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"menu", "option"})
    void customerReadsAndNewOrdersObserveSoldOutChanges(String action) throws Exception {
        invoke(action, storeId, target(action), owner, "{\"soldOut\":true}").andExpect(status().isOk());
        mvc.perform(get("/api/v1/stores/{storeId}/menus/{menuId}", storeId, menuId).header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("menu".equals(action) ? "$.data.soldOut"
                        : "$.data.optionGroups[0].options[0].soldOut").value(true));
        if ("menu".equals(action)) {
            mvc.perform(get("/api/v1/stores/{storeId}/menus", storeId))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].soldOut").value(true));
        }
        createOrder().andExpect("menu".equals(action) ? status().isConflict() : status().isBadRequest())
                .andExpect(jsonPath("$.code").value("menu".equals(action) ? "OD002" : "OD003"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE store_id=?", Integer.class, storeId)).isZero();
        invoke(action, storeId, target(action), owner, "{\"soldOut\":false}").andExpect(status().isOk());
        createOrder().andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"menu", "option"})
    void checkoutRechecksSoldOutBeforeIssuingAPickupNumber(String action) throws Exception {
        JsonNode order = mapper.readTree(createOrder().andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("data");
        long orderId = order.path("orderId").asLong();
        String pgOrderId = order.at("/payment/pgOrderId").asText();
        invoke(action, storeId, target(action), staff, "{\"soldOut\":true}").andExpect(status().isOk());
        confirm(orderId, pgOrderId).andExpect("menu".equals(action) ? status().isConflict() : status().isBadRequest())
                .andExpect(jsonPath("$.code").value("menu".equals(action) ? "OD002" : "OD003"));
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, orderId)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE order_id=?", String.class, orderId)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id=? AND to_status='REQUESTED'",
                Integer.class, orderId)).isZero();
        invoke(action, storeId, target(action), owner, "{\"soldOut\":false}").andExpect(status().isOk());
        confirm(orderId, pgOrderId).andExpect(status().isOk()).andExpect(jsonPath("$.data.pickupNo").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"menu", "option"})
    void concurrentIdenticalChangesAreBothSuccessful(String action) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return invoke(action, storeId, target(action), owner,
                    "{\"soldOut\":true}").andReturn().getResponse().getStatus(); });
            var second = pool.submit(() -> { start.await(); return invoke(action, storeId, target(action), staff,
                    "{\"soldOut\":true}").andReturn().getResponse().getStatus(); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(isSoldOut(action)).isTrue();
    }

    private ResultActions invoke(String action, long targetStore, long targetId, String auth, String body) throws Exception {
        MockHttpServletRequestBuilder request = switch (action) {
            case "list" -> get("/api/v1/staff/stores/{storeId}/menus", targetStore);
            case "menu" -> patch("/api/v1/staff/stores/{storeId}/menus/{menuId}/sold-out", targetStore, targetId);
            case "option" -> patch("/api/v1/staff/stores/{storeId}/option-items/{optionItemId}/sold-out", targetStore, targetId);
            default -> throw new IllegalArgumentException("Unknown test action");
        };
        if (auth != null) request.header("Authorization", auth);
        if (!"list".equals(action)) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private ResultActions createOrder() throws Exception {
        return mvc.perform(post("/api/v1/orders").header("Authorization", customer)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("storeId", storeId, "withdrawalLimitAgreed", true,
                        "items", List.of(Map.of("menuId", menuId, "quantity", 1, "optionItemIds", List.of(optionId)))))));
    }

    private ResultActions confirm(long orderId, String pgOrderId) throws Exception {
        return mvc.perform(post("/api/v1/dev/orders/{orderId}/confirm-payment", orderId)
                .header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("pgOrderId", pgOrderId))));
    }

    private long target(String action) { return "option".equals(action) ? optionId : menuId; }
    private boolean isSoldOut(String action) {
        return "option".equals(action)
                ? jdbc.queryForObject("SELECT is_sold_out FROM option_item WHERE id=?", Boolean.class, optionId)
                : jdbc.queryForObject("SELECT is_sold_out FROM menu_item WHERE id=?", Boolean.class, menuId);
    }
    private String authorization(AuthUser user) { return "Bearer " + tokens.encode(user, Instant.now()); }
}
