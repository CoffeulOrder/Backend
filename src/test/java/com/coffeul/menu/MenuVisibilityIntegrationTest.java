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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MenuVisibilityIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtAccessTokenService tokens;
    TestFixtures fixtures;
    long storeId;
    long memberId;
    String auth;

    @BeforeEach void prepare() {
        fixtures=new TestFixtures(jdbc);
        String unique=UUID.randomUUID().toString();
        long school=fixtures.createSchool("메뉴 노출 "+unique.substring(0,8));
        long merchant=fixtures.createMerchant(String.format("%010d",unique.hashCode()&Integer.MAX_VALUE),BigDecimal.ZERO);
        storeId=fixtures.createStore(merchant,school,"메뉴 노출 시험","OPEN");
        memberId=fixtures.createMember(school,unique+"@qa.example","ACTIVE");
        auth="Bearer "+tokens.encode(new AuthUser(memberId,AuthUser.SubjectType.MEMBER,AuthUser.Role.CUSTOMER,school,null),Instant.now());
    }

    @Test void hiddenMenusAreExcludedFromAllAndCategoryListsWhileSoldOutActiveMenusRemain() throws Exception {
        long active=fixtures.createMenuItem(storeId,"정상 메뉴",1500,false);
        long sold=fixtures.createMenuItem(storeId,"품절 메뉴",2000,true);
        long hidden=fixtures.createMenuItem(storeId,"숨김 메뉴",2500,false);
        jdbc.update("UPDATE menu_item SET status='HIDDEN' WHERE id=?",hidden);
        jdbc.update("INSERT INTO category(store_id,name) VALUES(?,'시험 카테고리')",storeId);
        long category=jdbc.queryForObject("SELECT id FROM category WHERE store_id=?",Long.class,storeId);
        for(long id:List.of(active,sold,hidden))jdbc.update("INSERT INTO menu_item_category(menu_item_id,category_id) VALUES(?,?)",id,category);
        for(String query:List.of("","?categoryId="+category)){var response=mvc.perform(get("/api/v1/stores/"+storeId+"/menus"+query).header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();
            var data=mapper.readTree(response.getResponse().getContentAsString()).path("data");
            var ids=new java.util.ArrayList<Long>();data.forEach(node->ids.add(node.path("menuId").asLong()));
            assertThat(ids).containsExactlyInAnyOrder(active,sold);
        }
    }

    @Test void hiddenMenuCannotCreateAnOrderAndLeavesNoOrderBehind() throws Exception {
        long hidden=fixtures.createMenuItem(storeId,"숨김 주문 차단",1500,false);
        jdbc.update("UPDATE menu_item SET status='HIDDEN' WHERE id=?",hidden);
        mvc.perform(post("/api/v1/orders").header("Authorization",auth).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("storeId",storeId,"withdrawalLimitAgreed",true,"items",List.of(Map.of("menuId",hidden,"quantity",1,"optionItemIds",List.of()))))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MN001"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id=?",Integer.class,memberId)).isZero();
    }

    @Test void storeWithOnlyHiddenMenusReturnsAnEmptyCustomerMenuList() throws Exception {
        long hidden=fixtures.createMenuItem(storeId,"숨김 전용",1500,false);
        jdbc.update("UPDATE menu_item SET status='HIDDEN' WHERE id=?",hidden);
        mvc.perform(get("/api/v1/stores/{id}/menus",storeId).header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }
}
