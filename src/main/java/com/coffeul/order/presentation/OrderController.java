package com.coffeul.order.presentation;

import com.coffeul.common.response.ApiResponse;
import com.coffeul.order.application.OrderCreateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * MS-16 참조 구현.
 * <p><b>임시</b>: auth 모듈이 아직 없어 회원 식별을 {@code X-Member-Id} 헤더로 받는다.
 * auth.api.AuthUser가 생기면 이 헤더는 없애고 인증 토큰에서 memberId를 뽑도록 바꾼다.
 */
@RestController
public class OrderController {

    private final OrderCreateService orderCreateService;

    public OrderController(OrderCreateService orderCreateService) {
        this.orderCreateService = orderCreateService;
    }

    @PostMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<OrderCreateResponse>> create(
            @RequestHeader("X-Member-Id") Long memberId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody OrderCreateRequest request) {
        OrderCreateService.Result result = orderCreateService.create(memberId, idempotencyKey, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        String message = result.created() ? "주문을 만들었어요. 결제를 진행해주세요." : "이미 만든 주문이에요.";
        return ResponseEntity.status(status).body(ApiResponse.success(message, result.body()));
    }
}
