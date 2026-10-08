package com.coffeul.order.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.order.application.OrderStaffActionService;
import com.coffeul.order.domain.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** MS-22(수락) · MS-24(제조 시작) · MS-25(픽업 대기) · MS-26(픽업 완료) 참조 구현. */
@RestController
public class OrderStaffController {

    private static final ZoneOffset KST = ZoneOffset.of("+09:00");

    private final OrderStaffActionService orderStaffActionService;

    public OrderStaffController(OrderStaffActionService orderStaffActionService) {
        this.orderStaffActionService = orderStaffActionService;
    }

    @PostMapping("/api/v1/staff/orders/{orderId}/accept")
    public ResponseEntity<ApiResponse<OrderAcceptResponse>> accept(AuthUser authUser, @PathVariable Long orderId) {
        Order order = orderStaffActionService.accept(authUser, orderId);
        return ResponseEntity.ok(ApiResponse.success("주문을 수락했어요.",
                new OrderAcceptResponse(order.getId(), order.getStatus(), toKst(order.getAcceptedAt()))));
    }

    @PostMapping("/api/v1/staff/orders/{orderId}/start")
    public ResponseEntity<ApiResponse<OrderStartResponse>> start(AuthUser authUser, @PathVariable Long orderId) {
        Order order = orderStaffActionService.start(authUser, orderId);
        return ResponseEntity.ok(ApiResponse.success("제조를 시작했어요.",
                new OrderStartResponse(order.getId(), order.getStatus(), toKst(order.getMakingAt()))));
    }

    @PostMapping("/api/v1/staff/orders/{orderId}/ready")
    public ResponseEntity<ApiResponse<OrderReadyResponse>> ready(AuthUser authUser, @PathVariable Long orderId) {
        Order order = orderStaffActionService.ready(authUser, orderId);
        return ResponseEntity.ok(ApiResponse.success("픽업 대기로 바꿨어요. 고객에게 알렸어요.",
                new OrderReadyResponse(order.getId(), order.getStatus(), toKst(order.getReadyAt()))));
    }

    @PostMapping("/api/v1/staff/orders/{orderId}/complete")
    public ResponseEntity<ApiResponse<OrderCompleteResponse>> complete(AuthUser authUser, @PathVariable Long orderId) {
        Order order = orderStaffActionService.complete(authUser, orderId);
        return ResponseEntity.ok(ApiResponse.success("픽업을 완료했어요.",
                new OrderCompleteResponse(order.getId(), order.getStatus(), toKst(order.getCompletedAt()))));
    }

    private static OffsetDateTime toKst(Instant instant) {
        return OffsetDateTime.ofInstant(instant, KST);
    }
}
