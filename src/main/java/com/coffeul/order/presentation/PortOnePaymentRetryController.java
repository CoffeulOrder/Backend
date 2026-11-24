package com.coffeul.order.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.order.application.PortOnePaymentRetryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "coffeul.payment.gateway", havingValue = "portone")
public class PortOnePaymentRetryController {
    private final PortOnePaymentRetryService service;

    public PortOnePaymentRetryController(PortOnePaymentRetryService service) { this.service = service; }

    @PostMapping("/api/v1/orders/{orderId}/payment-attempts")
    public ApiResponse<PortOnePaymentRetryService.Result> retry(AuthUser user, @PathVariable long orderId) {
        if (user.type() != AuthUser.SubjectType.MEMBER || user.role() != AuthUser.Role.CUSTOMER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        var result = service.retry(user.id(), orderId);
        return ApiResponse.success("OPEN_CHECKOUT".equals(result.action())
                ? "기존 결제번호로 다시 결제를 진행해주세요." : "접수된 주문을 확인해주세요.", result);
    }
}
