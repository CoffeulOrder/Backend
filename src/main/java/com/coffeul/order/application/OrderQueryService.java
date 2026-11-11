package com.coffeul.order.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.order.domain.Order;
import com.coffeul.order.domain.OrderLine;
import com.coffeul.order.domain.OrderLineOption;
import com.coffeul.order.infrastructure.OrderLineOptionRepository;
import com.coffeul.order.infrastructure.OrderLineRepository;
import com.coffeul.order.infrastructure.OrderRepository;
import com.coffeul.store.api.StoreQueryApi;
import com.coffeul.store.api.StoreView;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MS-17(내 주문 목록) · MS-18(주문 상세 · 대기 건수) — 고객앱 주문 내역 · 주문 상태 화면.
 * 읽기 전용이다. 취소 · 거절이 채우는 컬럼(canceled_at · rejected_at · 거절 사유)은 읽기만 한다.
 */
@Service
public class OrderQueryService {

    /** MS-18 aheadCount가 세는 상태. 접수 후 아직 픽업 대기에 이르지 못한 주문(명세 memo). */
    private static final List<String> WAITING_STATUSES = List.of(
            Order.STATUS_REQUESTED, Order.STATUS_ACCEPTED, Order.STATUS_MAKING);

    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderLineOptionRepository orderLineOptionRepository;
    private final StoreQueryApi storeQueryApi;

    public OrderQueryService(OrderRepository orderRepository,
                              OrderLineRepository orderLineRepository,
                              OrderLineOptionRepository orderLineOptionRepository,
                              StoreQueryApi storeQueryApi) {
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.orderLineOptionRepository = orderLineOptionRepository;
        this.storeQueryApi = storeQueryApi;
    }

    public record ListItem(Long orderId, String orderCode, String storeName, String status, Short pickupNo,
                            String itemSummary, int totalAmount, Instant placedAt) {
    }

    public record ListResult(List<ListItem> content, int page, int size, boolean hasNext) {
    }

    /**
     * MS-17. 최신순. 만료된 결제 대기(EXPIRED)만 뺀다 — 만료 전의 결제 대기는 결제창에서 이탈한 사용자가
     * 다시 들어올 수 있게 목록에 남긴다. 그래서 결제 대기 주문은 placedAt · pickupNo가 null이다(접수 전).
     */
    @Transactional(readOnly = true)
    public ListResult listMine(Long memberId, int page, int size) {
        // created_at이 같은 주문끼리도 순서가 흔들리지 않게 id를 함께 건다.
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Slice<Order> slice = orderRepository.findByMemberIdAndStatusNot(memberId, Order.STATUS_EXPIRED, pageable);

        Map<Long, String> storeNames = new HashMap<>();
        List<ListItem> content = slice.getContent().stream()
                .map(order -> new ListItem(order.getId(), order.getOrderCode(),
                        storeNames.computeIfAbsent(order.getStoreId(), this::storeNameOf),
                        order.getStatus(), order.getPickupNo(), itemSummaryOf(order.getId()),
                        order.getTotalAmount(), order.getPlacedAt()))
                .toList();
        return new ListResult(content, page, size, slice.hasNext());
    }

    public record DetailLine(String menuName, List<String> options, int unitPrice, int quantity, int lineAmount) {
    }

    public record Detail(Order order, String storeName, Long aheadCount, boolean cancelable, List<DetailLine> items) {
    }

    /** MS-18. 없는 주문과 남의 주문은 구분하지 않고 같은 404(OD005)로 답한다 — 남의 주문 존재를 드러내지 않는다. */
    @Transactional(readOnly = true)
    public Detail detail(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(found -> found.getMemberId().equals(memberId))
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

        Long aheadCount = null;
        if (WAITING_STATUSES.contains(order.getStatus()) && order.getPlacedAt() != null) {
            aheadCount = orderRepository.countByStoreIdAndStatusInAndPlacedAtLessThan(
                    order.getStoreId(), WAITING_STATUSES, order.getPlacedAt());
        }
        // 고객이 취소할 수 있는 건 매장이 아직 수락하지 않은 REQUESTED뿐이다(명세 memo).
        boolean cancelable = Order.STATUS_REQUESTED.equals(order.getStatus());

        return new Detail(order, storeNameOf(order.getStoreId()), aheadCount, cancelable, linesOf(order.getId()));
    }

    private String storeNameOf(Long storeId) {
        // orders.store_id는 FK라 없을 수 없다. 없다면 데이터가 깨진 것이니 조용히 넘기지 않고 드러낸다.
        return storeQueryApi.findById(storeId)
                .map(StoreView::name)
                .orElseThrow(() -> new IllegalStateException("주문이 가리키는 매장이 없다: storeId=" + storeId));
    }

    private List<DetailLine> linesOf(Long orderId) {
        return orderLineRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .map(line -> new DetailLine(line.getMenuNameSnapshot(), optionNamesOf(line.getId()),
                        line.getUnitPrice(), line.getQuantity(), line.getLineAmount()))
                .toList();
    }

    private List<String> optionNamesOf(Long orderLineId) {
        return orderLineOptionRepository.findByOrderLineIdOrderByIdAsc(orderLineId).stream()
                .map(OrderLineOption::getOptionItemNameSnapshot)
                .toList();
    }

    /** "아메리카노 외 1건" — OrderStaffBoardService · OrderCreateService의 orderName과 같은 규칙. */
    private String itemSummaryOf(Long orderId) {
        List<OrderLine> lines = orderLineRepository.findByOrderIdOrderByIdAsc(orderId);
        if (lines.isEmpty()) {
            return "";
        }
        return lines.size() == 1
                ? lines.get(0).getMenuNameSnapshot()
                : lines.get(0).getMenuNameSnapshot() + " 외 " + (lines.size() - 1) + "건";
    }
}
