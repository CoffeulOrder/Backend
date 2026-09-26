package com.coffeul.order.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.order.application.OrderQueryService;
import com.coffeul.order.domain.Order;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * MS-17(내 주문 목록) · MS-18(주문 상세 · 대기 건수) — 고객앱.
 * 인증은 AuthUser(Bearer 토큰)로 받는다. MS-16의 임시 X-Member-Id 헤더와 무관하다.
 */
@RestController
public class OrderQueryController {

    private static final ZoneOffset KST = ZoneOffset.of("+09:00");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final OrderQueryService orderQueryService;

    public OrderQueryController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @GetMapping("/api/v1/orders")
    public ApiResponse<OrderListResponse> list(AuthUser authUser,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Long memberId = customerId(authUser);
        validatePaging(page, size);

        OrderQueryService.ListResult result = orderQueryService.listMine(memberId, page, size);
        List<OrderListResponse.Item> content = result.content().stream()
                .map(item -> new OrderListResponse.Item(item.orderId(), item.orderCode(), item.storeName(),
                        item.status(), item.pickupNo(), item.itemSummary(), item.totalAmount(), toKst(item.placedAt())))
                .toList();
        return ApiResponse.success("주문 내역을 불러왔어요.",
                new OrderListResponse(content, result.page(), result.size(), result.hasNext()));
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public ApiResponse<OrderDetailResponse> detail(AuthUser authUser, @PathVariable Long orderId) {
        OrderQueryService.Detail detail = orderQueryService.detail(customerId(authUser), orderId);
        Order order = detail.order();

        List<OrderDetailResponse.Line> items = detail.items().stream()
                .map(line -> new OrderDetailResponse.Line(line.menuName(), line.options(), line.unitPrice(),
                        line.quantity(), line.lineAmount()))
                .toList();
        OrderDetailResponse.Timeline timeline = new OrderDetailResponse.Timeline(
                toKst(order.getPlacedAt()), toKst(order.getAcceptedAt()), toKst(order.getMakingAt()),
                toKst(order.getReadyAt()), toKst(order.getCompletedAt()), toKst(order.getCanceledAt()),
                toKst(order.getRejectedAt()));

        return ApiResponse.success("주문을 불러왔어요.", new OrderDetailResponse(
                order.getId(), order.getOrderCode(), order.getStatus(),
                new OrderDetailResponse.Store(order.getStoreId(), detail.storeName()), order.getPickupNo(),
                detail.aheadCount(), detail.cancelable(), items,
                order.getSubtotalAmount(), order.getDiscountAmount(), order.getTotalAmount(),
                order.getRequestMemo(), order.getRejectReasonCode(), timeline));
    }

    /**
     * 이 두 API의 권한은 CUSTOMER다. 직원 토큰(typ=STAFF)의 sub는 staff_account.id인데, 그대로 쓰면
     * 같은 숫자의 member.id로 남의 주문을 읽게 된다. 그래서 주체 종류를 먼저 확인한다(MemberController와 같은 방어).
     */
    private Long customerId(AuthUser authUser) {
        if (authUser.type() != AuthUser.SubjectType.MEMBER || authUser.role() != AuthUser.Role.CUSTOMER) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return authUser.id();
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw invalidPaging("page", "page는 0 이상이에요.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw invalidPaging("size", "size는 1 이상 " + MAX_PAGE_SIZE + " 이하예요.");
        }
    }

    private BusinessException invalidPaging(String field, String reason) {
        return new BusinessException(CommonErrorCode.VALIDATION_FAILED, reason,
                List.of(new ApiResponse.FieldError(field, reason)));
    }

    private OffsetDateTime toKst(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, KST);
    }
}
