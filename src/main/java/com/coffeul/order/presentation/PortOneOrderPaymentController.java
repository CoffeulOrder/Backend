package com.coffeul.order.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.order.application.PortOneOrderPaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@ConditionalOnProperty(name = "coffeul.payment.gateway", havingValue = "portone")
public class PortOneOrderPaymentController {
    private final PortOneOrderPaymentService service;

    public PortOneOrderPaymentController(PortOneOrderPaymentService service) {
        this.service = service;
    }

    public record Request(@NotNull @Positive Long orderId,
                          @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{6,64}") String paymentId) { }

    public record Response(Long orderId, String orderCode, String status, Short pickupNo, Payment payment) { }

    public record Payment(String status, int amount, String method, Instant approvedAt) { }

    @PostMapping("/api/v1/payments/confirm")
    public ApiResponse<Response> confirm(AuthUser user, @Valid @RequestBody Request request) {
        if (user.type() != AuthUser.SubjectType.MEMBER || user.role() != AuthUser.Role.CUSTOMER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        var result = service.confirm(user.id(), request.orderId(), request.paymentId());
        var order = result.order();
        return ApiResponse.success("결제를 확인했어요.", new Response(order.getId(), order.getOrderCode(),
                order.getStatus(), order.getPickupNo(),
                new Payment("APPROVED", result.amount(), result.method(), result.approvedAt())));
    }
}
