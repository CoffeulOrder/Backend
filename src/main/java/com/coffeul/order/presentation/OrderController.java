package com.coffeul.order.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.order.application.OrderCreateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * MS-16 주문 생성. 회원은 검증된 Bearer 토큰으로 식별한다.
 */
@RestController
public class OrderController {

    private final OrderCreateService orderCreateService;

    public OrderController(OrderCreateService orderCreateService) {
        this.orderCreateService = orderCreateService;
    }

    @PostMapping("/api/v1/orders")
    public ResponseEntity<ApiResponse<OrderCreateResponse>> create(
            AuthUser authUser,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody OrderCreateRequest request) {
        if (authUser.type() != AuthUser.SubjectType.MEMBER || authUser.role() != AuthUser.Role.CUSTOMER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        OrderCreateService.Result result = orderCreateService.create(authUser.id(), idempotencyKey, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        String message = result.created() ? "주문을 만들었어요. 결제를 진행해주세요." : "이미 만든 주문이에요.";
        return ResponseEntity.status(status).body(ApiResponse.success(message, result.body()));
    }
}
