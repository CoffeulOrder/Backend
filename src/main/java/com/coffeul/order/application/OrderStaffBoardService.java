package com.coffeul.order.application;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.order.domain.Order;
import com.coffeul.order.domain.OrderLine;
import com.coffeul.order.domain.OrderLineOption;
import com.coffeul.order.infrastructure.OrderLineOptionRepository;
import com.coffeul.order.infrastructure.OrderLineRepository;
import com.coffeul.order.infrastructure.OrderRepository;
import com.coffeul.store.api.StoreAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * MS-20(진행 중 주문 목록 · 폴링) · MS-21(날짜별 주문 내역) — 관리자앱 주문 보드.
 */
@Service
public class OrderStaffBoardService {

    /** 접수 ~ 픽업 대기 (MS-20 범위, rules.py와 동일하게 REQ-ST-004의 "처리 중" 정의를 그대로 씀). */
    private static final List<String> IN_PROGRESS_STATUSES = List.of(
            Order.STATUS_REQUESTED, Order.STATUS_ACCEPTED, Order.STATUS_MAKING, Order.STATUS_READY);

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderLineOptionRepository orderLineOptionRepository;
    private final StoreAccessPolicy storeAccessPolicy;

    public OrderStaffBoardService(OrderRepository orderRepository,
                                   OrderLineRepository orderLineRepository,
                                   OrderLineOptionRepository orderLineOptionRepository,
                                   StoreAccessPolicy storeAccessPolicy) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.orderLineOptionRepository = orderLineOptionRepository;
        this.storeAccessPolicy = storeAccessPolicy;
    }

    public record BoardLine(String menuName, List<String> options, int quantity) {
    }

    public record BoardOrder(Long orderId, String orderCode, Short pickupNo, String status,
                              java.time.Instant placedAt, List<BoardLine> items, String requestMemo) {
    }

    /** MS-20. */
    @Transactional(readOnly = true)
    public List<BoardOrder> listActive(AuthUser staff, Long storeId) {
        storeAccessPolicy.check(staff, storeId);
        return orderRepository.findByStoreIdAndStatusInOrderByPlacedAtAsc(storeId, IN_PROGRESS_STATUSES).stream()
                .map(order -> new BoardOrder(order.getId(), order.getOrderCode(), order.getPickupNo(), order.getStatus(),
                        order.getPlacedAt(), itemLinesOf(order.getId()), order.getRequestMemo()))
                .toList();
    }

    public record HistorySummary(long completed, long canceled, long rejected, long inProgress) {
    }

    public record HistoryOrder(Long orderId, Short pickupNo, String status, int totalAmount, String itemSummary,
                                java.time.Instant placedAt, java.time.Instant completedAt) {
    }

    public record HistoryResult(HistorySummary summary, List<HistoryOrder> orders) {
    }

    /** MS-21. */
    @Transactional(readOnly = true)
    public HistoryResult listByDate(AuthUser staff, Long storeId, LocalDate businessDate) {
        storeAccessPolicy.check(staff, storeId);
        List<Order> orders = orderRepository.findByStoreIdAndBusinessDate(storeId, businessDate);

        long completed = orders.stream().filter(o -> Order.STATUS_COMPLETED.equals(o.getStatus())).count();
        long canceled = orders.stream().filter(o -> Order.STATUS_CANCELED.equals(o.getStatus())).count();
        long rejected = orders.stream().filter(o -> Order.STATUS_REJECTED.equals(o.getStatus())).count();
        long inProgress = orders.stream().filter(o -> IN_PROGRESS_STATUSES.contains(o.getStatus())).count();

        List<HistoryOrder> historyOrders = orders.stream()
                .map(order -> new HistoryOrder(order.getId(), order.getPickupNo(), order.getStatus(),
                        order.getTotalAmount(), itemSummaryOf(order.getId()), order.getPlacedAt(), order.getCompletedAt()))
                .toList();
        return new HistoryResult(new HistorySummary(completed, canceled, rejected, inProgress), historyOrders);
    }

    private List<BoardLine> itemLinesOf(Long orderId) {
        return orderLineRepository.findByOrderId(orderId).stream()
                .map(line -> new BoardLine(line.getMenuNameSnapshot(), optionNamesOf(line.getId()), line.getQuantity()))
                .toList();
    }

    private List<String> optionNamesOf(Long orderLineId) {
        return orderLineOptionRepository.findByOrderLineId(orderLineId).stream()
                .map(OrderLineOption::getOptionItemNameSnapshot)
                .toList();
    }

    /** "아메리카노 외 1건" 형태 — OrderCreateService의 orderName 조립과 같은 규칙. */
    private String itemSummaryOf(Long orderId) {
        List<OrderLine> lines = orderLineRepository.findByOrderId(orderId);
        if (lines.isEmpty()) {
            return "";
        }
        return lines.size() == 1
                ? lines.get(0).getMenuNameSnapshot()
                : lines.get(0).getMenuNameSnapshot() + " 외 " + (lines.size() - 1) + "건";
    }
}
