package com.coffeul.order.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.order.application.OrderStaffBoardService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;

/** MS-20(진행 중 주문 목록 · 폴링) · MS-21(날짜별 주문 내역) 참조 구현 — 관리자앱 주문 보드. */
@RestController
public class OrderStaffBoardController {

    private static final ZoneOffset KST = ZoneOffset.of("+09:00");

    private final OrderStaffBoardService orderStaffBoardService;
    private final ZoneId businessZone;

    public OrderStaffBoardController(OrderStaffBoardService orderStaffBoardService,
                                      @Value("${coffeul.business-zone:Asia/Seoul}") String businessZone) {
        this.orderStaffBoardService = orderStaffBoardService;
        this.businessZone = ZoneId.of(businessZone);
    }

    @GetMapping("/api/v1/staff/stores/{storeId}/orders/active")
    public ApiResponse<OrderBoardResponse> active(AuthUser authUser, @PathVariable Long storeId) {
        List<OrderStaffBoardService.BoardOrder> boardOrders = orderStaffBoardService.listActive(authUser, storeId);
        Instant now = Instant.now();

        List<OrderBoardResponse.Item> items = boardOrders.stream()
                .map(order -> new OrderBoardResponse.Item(order.orderId(), order.orderCode(), order.pickupNo(),
                        order.status(), toKst(order.placedAt()), elapsedSeconds(order.placedAt(), now),
                        order.items().stream()
                                .map(line -> new OrderBoardResponse.LineItem(line.menuName(), line.options(), line.quantity()))
                                .toList(),
                        order.requestMemo()))
                .toList();
        return ApiResponse.success("진행 중 주문을 불러왔어요.", new OrderBoardResponse(toKst(now), items));
    }

    @GetMapping("/api/v1/staff/stores/{storeId}/orders")
    public ApiResponse<OrderHistoryResponse> history(AuthUser authUser, @PathVariable Long storeId,
                                                       @RequestParam(required = false) String date) {
        LocalDate businessDate = parseBusinessDate(date);
        OrderStaffBoardService.HistoryResult result = orderStaffBoardService.listByDate(authUser, storeId, businessDate);

        OrderHistoryResponse.Summary summary = new OrderHistoryResponse.Summary(
                result.summary().completed(), result.summary().canceled(), result.summary().rejected(), result.summary().inProgress());
        List<OrderHistoryResponse.Item> items = result.orders().stream()
                .map(order -> new OrderHistoryResponse.Item(order.orderId(), order.pickupNo(), order.status(),
                        order.totalAmount(), order.itemSummary(), toKst(order.placedAt()), toKst(order.completedAt())))
                .toList();
        return ApiResponse.success("주문 내역을 불러왔어요.", new OrderHistoryResponse(businessDate, summary, items));
    }

    private LocalDate parseBusinessDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(businessZone);
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED, "날짜 형식은 YYYY-MM-DD예요.",
                    List.of(new ApiResponse.FieldError("date", "날짜 형식은 YYYY-MM-DD예요.")));
        }
    }

    private long elapsedSeconds(Instant placedAt, Instant now) {
        return placedAt == null ? 0 : Duration.between(placedAt, now).getSeconds();
    }

    private OffsetDateTime toKst(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, KST);
    }
}
