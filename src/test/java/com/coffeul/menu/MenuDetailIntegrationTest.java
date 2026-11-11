package com.coffeul.menu;

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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MenuDetailIntegrationTest extends AbstractIntegrationTest {

    private static final String MENU_DETAIL_PATH = "/api/v1/stores/{storeId}/menus/{menuId}";

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
    private String customerAuthorization;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        String unique = UUID.randomUUID().toString();
        schoolId = fixtures.createSchool("메뉴 상세 테스트-" + unique.substring(0, 8));
        merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & Integer.MAX_VALUE),
                new BigDecimal("0.0300"));
        storeId = fixtures.createStore(merchantId, schoolId, "테스트 매장", "OPEN");
        long memberId = fixtures.createMember(schoolId, unique + "@test.ac.kr", "ACTIVE");
        customerAuthorization = authorization(new AuthUser(memberId, AuthUser.SubjectType.MEMBER,
                AuthUser.Role.CUSTOMER, schoolId, null));
    }

    @Test
    void 메뉴_상세에서_받은_옵션_ID와_추가금으로_주문할_수_있다() throws Exception {
        long menuId = fixtures.createMenuItem(storeId, "카페라떼", 2500, false);
        jdbcTemplate.update("UPDATE menu_item SET description = ?, image_key = ? WHERE id = ?",
                "우유를 더한 라떼", "menu/demo-latte.webp", menuId);
        long groupId = fixtures.createOptionGroup(menuId, "사이즈", "SIZE", true, 1, 1);
        long defaultOptionId = fixtures.createOptionItem(groupId, "R", 0, true);
        long largeOptionId = fixtures.createOptionItem(groupId, "L", 500, false);
        jdbcTemplate.update("UPDATE option_item SET sort_order = ? WHERE id = ?", 1, largeOptionId);
        long soldOutOptionId = fixtures.createOptionItem(groupId, "XL", 1000, false);
        jdbcTemplate.update("UPDATE option_item SET sort_order = ?, is_sold_out = TRUE WHERE id = ?",
                2, soldOutOptionId);

        String response = detail(storeId, menuId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuId").value(menuId))
                .andExpect(jsonPath("$.data.name").value("카페라떼"))
                .andExpect(jsonPath("$.data.description").value("우유를 더한 라떼"))
                .andExpect(jsonPath("$.data.basePrice").value(2500))
                .andExpect(jsonPath("$.data.imageUrl", endsWith("/menu/demo-latte.webp")))
                .andExpect(jsonPath("$.data.soldOut").value(false))
                .andExpect(jsonPath("$.data.optionGroups[0].optionGroupId").value(groupId))
                .andExpect(jsonPath("$.data.optionGroups[0].name").value("사이즈"))
                .andExpect(jsonPath("$.data.optionGroups[0].type").value("SIZE"))
                .andExpect(jsonPath("$.data.optionGroups[0].required").value(true))
                .andExpect(jsonPath("$.data.optionGroups[0].minSelect").value(1))
                .andExpect(jsonPath("$.data.optionGroups[0].maxSelect").value(1))
                .andExpect(jsonPath("$.data.optionGroups[0].options[0].optionItemId").value(defaultOptionId))
                .andExpect(jsonPath("$.data.optionGroups[0].options[0].isDefault").value(true))
                .andExpect(jsonPath("$.data.optionGroups[0].options[1].optionItemId").value(largeOptionId))
                .andExpect(jsonPath("$.data.optionGroups[0].options[1].priceDelta").value(500))
                .andExpect(jsonPath("$.data.optionGroups[0].options[2].optionItemId").value(soldOutOptionId))
                .andExpect(jsonPath("$.data.optionGroups[0].options[2].soldOut").value(true))
                .andReturn().getResponse().getContentAsString();

        long selectedOptionId = objectMapper.readTree(response)
                .at("/data/optionGroups/0/options/1/optionItemId").asLong();
        String orderBody = objectMapper.writeValueAsString(Map.of(
                "storeId", storeId,
                "items", List.of(Map.of("menuId", menuId, "quantity", 2, "optionItemIds", List.of(selectedOptionId))),
                "withdrawalLimitAgreed", true));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", customerAuthorization)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(6000));
    }

    @Test
    void 옵션_없는_품절_메뉴도_상세에서_상태를_확인할_수_있다() throws Exception {
        long menuId = fixtures.createMenuItem(storeId, "아이스티", 2000, true);

        detail(storeId, menuId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.soldOut").value(true))
                .andExpect(jsonPath("$.data.optionGroups").isEmpty());
    }

    @Test
    void 없는_메뉴와_숨김_메뉴와_다른_매장_메뉴는_동일한_404를_반환한다() throws Exception {
        long hiddenMenuId = fixtures.createMenuItem(storeId, "숨김 메뉴", 2000, false);
        jdbcTemplate.update("UPDATE menu_item SET status = 'HIDDEN' WHERE id = ?", hiddenMenuId);
        long otherStoreId = fixtures.createStore(merchantId, schoolId, "다른 테스트 매장", "OPEN");
        long otherMenuId = fixtures.createMenuItem(otherStoreId, "다른 매장 메뉴", 2000, false);

        for (long unavailableId : List.of(Long.MAX_VALUE, hiddenMenuId, otherMenuId)) {
            detail(storeId, unavailableId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("C004"));
        }
    }

    @Test
    void 메뉴_상세는_고객_Bearer_인증이_필요하다() throws Exception {
        long menuId = fixtures.createMenuItem(storeId, "아메리카노", 1500, false);
        String staffAuthorization = authorization(new AuthUser(1L, AuthUser.SubjectType.STAFF,
                AuthUser.Role.OWNER, null, merchantId));

        mockMvc.perform(get(MENU_DETAIL_PATH, storeId, menuId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("C002"));
        mockMvc.perform(get(MENU_DETAIL_PATH, storeId, menuId).header("Authorization", staffAuthorization))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    private ResultActions detail(long storeId, long menuId) throws Exception {
        return mockMvc.perform(get(MENU_DETAIL_PATH, storeId, menuId)
                .header("Authorization", customerAuthorization));
    }

    private String authorization(AuthUser user) {
        return "Bearer " + jwtAccessTokenService.encode(user, Instant.now());
    }
}
