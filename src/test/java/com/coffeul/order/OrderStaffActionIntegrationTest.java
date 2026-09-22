package com.coffeul.order;

import com.coffeul.AbstractIntegrationTest;
import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.infrastructure.JwtAccessTokenService;
import com.coffeul.order.api.OrderReadyEvent;
import com.coffeul.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MS-22(수락) · MS-24(제조 시작) · MS-25(픽업 대기) · MS-26(픽업 완료) 엔드투엔드 검증.
 * MS-27(결제 승인)이 아직 없어서 REQUESTED 주문은 TestFixtures.createOrder로 직접 심는다.
 */
@RecordApplicationEvents
class OrderStaffActionIntegrationTest extends AbstractIntegrationTest {

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
    private long memberId;
    private String staffAuthHeader;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures(jdbcTemplate);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        schoolId = fixtures.createSchool("테스트대학교-" + unique);
        merchantId = fixtures.createMerchant(String.format("%010d", unique.hashCode() & 0x7fffffff),
                new BigDecimal("0.0300"));
        storeId = fixtures.createStore(merchantId, schoolId, "테스트매장", "OPEN");
        memberId = fixtures.createMember(schoolId, "member-" + unique + "@test.ac.kr", "ACTIVE");

        long staffId = fixtures.createStaffAccount(merchantId, "STAFF", "staff-" + unique, "pw1234!!",
                "테스트직원", "ACTIVE");
        fixtures.createStaffStore(staffId, storeId);
        staffAuthHeader = authHeaderFor(new AuthUser(staffId, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, merchantId));
    }

    private String authHeaderFor(AuthUser user) {
        return "Bearer " + jwtAccessTokenService.encode(user, Instant.now());
    }

    @Test
    void 수락하면_ACCEPTED로_바뀌고_이력이_남는다() throws Exception {
        long orderId = fixtures.createOrder(memberId, storeId, schoolId, "REQUESTED");

        mockMvc.perform(post("/api/v1/staff/orders/{orderId}/accept", orderId).header("Authorization", staffAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.acceptedAt").exists());

        Map<String, Object> orderRow = jdbcTemplate.queryForMap("SELECT * FROM `orders` WHERE `id` = ?", orderId);
        assertThat(orderRow.get("status")).isEqualTo("ACCEPTED");
        assertThat(orderRow.get("accepted_at")).isNotNull();

        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT * FROM `order_status_history` WHERE `order_id` = ?", orderId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("from_status")).isEqualTo("REQUESTED");
        assertThat(history.get(0).get("to_status")).isEqualTo("ACCEPTED");
        assertThat(history.get(0).get("actor_type")).isEqualTo("STAFF");
    }

    @Test
    void 같은_요청을_두_번_보내도_두_번째는_그대로_200이고_이력은_한_번만_남는다() throws Exception {
        long orderId = fixtures.createOrder(memberId, storeId, schoolId, "REQUESTED");

        mockMvc.perform(post("/api/v1/staff/orders/{orderId}/accept", orderId).header("Authorization", staffAuthHeader))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/staff/orders/{orderId}/accept", orderId).header("Authorization", staffAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order_status_history` WHERE `order_id` = ?", Integer.class, orderId);
        assertThat(historyCount).isEqualTo(1);
    }

    @Test
    void 수락_제조시작_픽업대기_완료까지_체인으로_전이되고_픽업대기에서_이벤트가_발행된다(ApplicationEvents events) throws Exception {
        long orderId = fixtures.createOrder(memberId, storeId, schoolId, "REQUESTED");

        mockMvc.perform(post("/api/v1/staff/orders/{orderId}/accept", orderId).header("Authorization", staffAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        mockMvc.perform(post("/api/v1/staff/orders/{orderId}/start", orderId).header("Authorization", staffAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MAKING"));
        mockMvc.perform(post("/api/v1/staff/orders/{orderId}/ready", orderId).header("Authorization", staffAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"));
        mockMvc.perform(post("/api/v1/staff/orders/{orderId}/complete", orderId).header("Authorization", staffAuthHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order_status_history` WHERE `order_id` = ?", Integer.class, orderId);
        assertThat(historyCount).isEqualTo(4);

        long orderReadyEvents = events.stream(OrderReadyEvent.class)
                .filter(event -> event.orderId().equals(orderId))
                .count();
        assertThat(orderReadyEvents).isEqualTo(1);
    }

    @Test
    void 순서가_아니면_409와_현재_상태를_돌려준다() throws Exception {
        long orderId = fixtures.createOrder(memberId, storeId, schoolId, "REQUESTED");

        // ACCEPTED를 거치지 않고 바로 제조 시작 시도
        mockMvc.perform(post("/api/v1/staff/orders/{orderId}/start", orderId).header("Authorization", staffAuthHeader))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OD006"))
                .andExpect(jsonPath("$.data.currentStatus").value("REQUESTED"));
    }

    @Test
    void 없는_주문은_404다() throws Exception {
        mockMvc.perform(post("/api/v1/staff/orders/{orderId}/accept", 999_999_999L).header("Authorization", staffAuthHeader))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OD005"));
    }

    @Test
    void 다른_사장님_직원이면_403이다() throws Exception {
        long orderId = fixtures.createOrder(memberId, storeId, schoolId, "REQUESTED");

        long otherMerchantId = fixtures.createMerchant(
                String.format("%010d", UUID.randomUUID().toString().hashCode() & 0x7fffffff), new BigDecimal("0.0300"));
        long otherStaffId = fixtures.createStaffAccount(otherMerchantId, "STAFF", "other-" + UUID.randomUUID(),
                "pw1234!!", "다른매장직원", "ACTIVE");
        // otherStaffId는 storeId에 staff_store로 배정돼 있지 않다.
        String otherStaffAuth = authHeaderFor(
                new AuthUser(otherStaffId, AuthUser.SubjectType.STAFF, AuthUser.Role.STAFF, null, otherMerchantId));

        mockMvc.perform(post("/api/v1/staff/orders/{orderId}/accept", orderId).header("Authorization", otherStaffAuth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test
    void 동시에_같은_요청을_보내도_이력은_한_번만_남는다() throws Exception {
        long orderId = fixtures.createOrder(memberId, storeId, schoolId, "REQUESTED");
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicIntegerArray statuses = new AtomicIntegerArray(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                int index = i;
                executor.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        int status = mockMvc.perform(post("/api/v1/staff/orders/{orderId}/accept", orderId)
                                        .header("Authorization", staffAuthHeader))
                                .andReturn().getResponse().getStatus();
                        statuses.set(index, status);
                    } catch (Exception e) {
                        statuses.set(index, -1);
                    }
                });
            }
            ready.await();
            start.countDown();
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        for (int i = 0; i < threadCount; i++) {
            assertThat(statuses.get(i)).isEqualTo(200);
        }
        Integer historyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `order_status_history` WHERE `order_id` = ?", Integer.class, orderId);
        assertThat(historyCount).isEqualTo(1);
    }
}
