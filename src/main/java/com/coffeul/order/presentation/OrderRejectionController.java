package com.coffeul.order.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.order.application.OrderRejectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class OrderRejectionController {

    private final OrderRejectionService service;

    public OrderRejectionController(OrderRejectionService service) {
        this.service = service;
    }

    public record Request(@NotBlank @Size(max = 30) String reasonCode, @Size(max = 200) String reasonDetail) {
    }

    public record Response(Long orderId, String status, String reasonCode, String reasonDetail,
                           OffsetDateTime rejectedAt, String refundStatus, int refundAmount) {
    }

    @PostMapping("/api/v1/staff/orders/{orderId}/reject")
    public ResponseEntity<ApiResponse<Response>> reject(AuthUser staff, @PathVariable Long orderId,
                                                        @Valid @RequestBody Request request) {
        var result = service.reject(staff, orderId, request.reasonCode(), request.reasonDetail());
        var order = result.order();
        var refund = result.refund();
        HttpStatus status = "REQUESTED".equals(refund.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        String message = switch (refund.status()) {
            case "SUCCEEDED" -> "주문을 거절하고 환불을 처리했어요.";
            case "FAILED" -> "주문은 거절됐어요. 환불 처리에 확인이 필요해요.";
            default -> "주문을 거절했어요. 환불을 처리하고 있어요.";
        };
        var response = new Response(order.getId(), order.getStatus(), order.getRejectReasonCode(),
                order.getRejectReasonDetail(), OffsetDateTime.ofInstant(order.getRejectedAt(), ZoneOffset.ofHours(9)),
                refund.status(), refund.amount());
        return ResponseEntity.status(status)
                .body(new ApiResponse<>(OffsetDateTime.now(), status.value(), "SUCCESS", message, response, null));
    }
}
