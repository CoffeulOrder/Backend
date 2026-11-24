package com.coffeul.order.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.order.application.OrderPaymentStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderPaymentStatusController {
    private final OrderPaymentStatusService service;

    public OrderPaymentStatusController(OrderPaymentStatusService service) { this.service = service; }

    @GetMapping("/api/v1/orders/{orderId}/payment")
    public ApiResponse<OrderPaymentStatusService.Result> get(AuthUser user, @PathVariable long orderId) {
        if (user.type() != AuthUser.SubjectType.MEMBER || user.role() != AuthUser.Role.CUSTOMER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return ApiResponse.success("결제 상태를 확인했어요.", service.get(user.id(), orderId));
    }
}
