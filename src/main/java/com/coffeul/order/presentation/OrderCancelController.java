package com.coffeul.order.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.order.application.OrderCancelService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
public class OrderCancelController {

    private final OrderCancelService service;

    public OrderCancelController(OrderCancelService service) {
        this.service = service;
    }

    public record Request(@Size(max = 200) String reason) {
    }

    public record Response(Long orderId, String orderCode, String status, OffsetDateTime canceledAt,
                           String refundStatus, int refundAmount) {
    }

    @PostMapping("/api/v1/orders/{orderId}/cancel")
    public ResponseEntity<ApiResponse<Response>> cancel(AuthUser user, @PathVariable Long orderId,
                                                        @Valid @RequestBody(required = false) Request request) {
        if (user.type() != AuthUser.SubjectType.MEMBER || user.role() != AuthUser.Role.CUSTOMER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "고객 요청" : request.reason().strip();
        var result = service.cancel(user.id(), orderId, reason);
        var order = result.order();
        var cancellation = result.cancellation();
        var response = new Response(order.getId(), order.getOrderCode(), order.getStatus(),
                OffsetDateTime.ofInstant(order.getCanceledAt(), ZoneOffset.ofHours(9)),
                cancellation.status(), cancellation.amount());
        HttpStatus status = "REQUESTED".equals(cancellation.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        String message = switch (cancellation.status()) {
            case "SUCCEEDED" -> "주문을 취소하고 환불을 처리했어요.";
            case "FAILED" -> "주문은 취소됐어요. 환불 처리에 확인이 필요해요.";
            default -> "주문을 취소했어요. 환불을 처리하고 있어요.";
        };
        return ResponseEntity.status(status)
                .body(new ApiResponse<>(OffsetDateTime.now(), status.value(), "SUCCESS", message, response, null));
    }
}
