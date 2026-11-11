package com.coffeul.order.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.order.application.DevOrderPaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@Profile("dev & !prod & !production")
@ConditionalOnProperty(name = "coffeul.dev.payment-flow-enabled", havingValue = "true")
public class DevOrderPaymentController {
    private final DevOrderPaymentService service;
    public DevOrderPaymentController(DevOrderPaymentService service) { this.service = service; }

    public record Request(@NotBlank @Size(max = 64) String pgOrderId) { }
    public record Response(Long orderId, String orderCode, String status, Short pickupNo,
                            Instant placedAt, Payment payment, boolean simulated) { }
    public record Payment(String provider, String status, int amount, Instant approvedAt) { }

    @PostMapping("/api/v1/dev/orders/{orderId}/confirm-payment")
    public ApiResponse<Response> confirm(AuthUser user, @PathVariable Long orderId,
                                          @Valid @RequestBody Request request) {
        if (user.type() != AuthUser.SubjectType.MEMBER || user.role() != AuthUser.Role.CUSTOMER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        var result = service.confirm(user.id(), orderId, request.pgOrderId());
        var order = result.order();
        var payment = result.payment();
        return ApiResponse.success("개발용 가짜 결제를 확인했어요. 실제 결제는 발생하지 않았어요.",
                new Response(order.getId(), order.getOrderCode(), order.getStatus(), order.getPickupNo(),
                        order.getPlacedAt(), new Payment("FAKE", payment.status(), payment.amount(), payment.approvedAt()), true));
    }
}
