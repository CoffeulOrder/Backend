package com.coffeul.order.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.member.api.MemberStatusApi;
import com.coffeul.menu.api.MenuItemView;
import com.coffeul.menu.api.MenuQueryApi;
import com.coffeul.order.domain.InvalidOptionSelectionException;
import com.coffeul.order.domain.LinePricing;
import com.coffeul.order.domain.MenuSelectionValidator;
import com.coffeul.order.domain.Order;
import com.coffeul.order.domain.OrderLine;
import com.coffeul.order.domain.OrderLineOption;
import com.coffeul.order.domain.OrderStatusHistory;
import com.coffeul.order.infrastructure.OrderLineOptionRepository;
import com.coffeul.order.infrastructure.OrderLineRepository;
import com.coffeul.order.infrastructure.OrderRepository;
import com.coffeul.order.infrastructure.OrderStatusHistoryRepository;
import com.coffeul.order.presentation.OrderCreateRequest;
import com.coffeul.order.presentation.OrderCreateResponse;
import com.coffeul.payment.api.PaymentAttemptView;
import com.coffeul.payment.api.PaymentPreparationApi;
import com.coffeul.store.api.StoreErrorCode;
import com.coffeul.store.api.StoreQueryApi;
import com.coffeul.store.api.StoreView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MS-16(주문 생성) 기준 구현.
 * <p><b>알려진 한계</b> (커밋·README 참고): 아직 auth 모듈이 없어 memberId는 컨트롤러가 임시 헤더로 받고,
 * merchant_pg 실데이터가 없어 {@code payment} 행은 영속화하지 않는다(응답의 payment 블록은 계산만).
 */
@Service
public class OrderCreateService {

    private final StoreQueryApi storeQueryApi;
    private final MenuQueryApi menuQueryApi;
    private final PaymentPreparationApi paymentPreparationApi;
    private final MemberStatusApi memberStatusApi;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final OrderLineOptionRepository orderLineOptionRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final long paymentExpireMinutes;

    public OrderCreateService(StoreQueryApi storeQueryApi,
                               MenuQueryApi menuQueryApi,
                               PaymentPreparationApi paymentPreparationApi,
                               MemberStatusApi memberStatusApi,
                               OrderRepository orderRepository,
                               OrderLineRepository orderLineRepository,
                               OrderLineOptionRepository orderLineOptionRepository,
                               OrderStatusHistoryRepository orderStatusHistoryRepository,
                               @Value("${coffeul.order.payment-expire-minutes:20}") long paymentExpireMinutes) {
        this.storeQueryApi = storeQueryApi;
        this.menuQueryApi = menuQueryApi;
        this.paymentPreparationApi = paymentPreparationApi;
        this.memberStatusApi = memberStatusApi;
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
        this.orderLineOptionRepository = orderLineOptionRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.paymentExpireMinutes = paymentExpireMinutes;
    }

    public record Result(OrderCreateResponse body, boolean created) {
    }

    @Transactional
    public Result create(Long memberId, String idempotencyKey, OrderCreateRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED, "메뉴를 하나 이상 담아주세요.",
                    List.of(new ApiResponse.FieldError("items", "메뉴를 하나 이상 담아주세요.")));
        }
        if (request.items().size() > 20) {
            throw BusinessException.withData(OrderErrorCode.QUANTITY_LIMIT_EXCEEDED,
                    OrderErrorCode.QUANTITY_LIMIT_EXCEEDED.message(), Map.of("maxQuantityPerItem", 20, "maxItems", 20));
        }
        if (!Boolean.TRUE.equals(request.withdrawalLimitAgreed())) {
            throw new BusinessException(OrderErrorCode.WITHDRAWAL_LIMIT_NOT_AGREED);
        }

        String requestHash = RequestHash.of(request);
        Optional<Order> existing = orderRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey);
        if (existing.isPresent()) {
            return new Result(replay(existing.get(), requestHash), false);
        }

        if (!memberStatusApi.isActive(memberId)) {
            throw new BusinessException(OrderErrorCode.MEMBER_NOT_USABLE);
        }

        StoreView store = storeQueryApi.findById(request.storeId())
                .orElseThrow(() -> new BusinessException(StoreErrorCode.NOT_FOUND));
        if (!store.isOpen()) {
            throw BusinessException.withData(StoreErrorCode.NOT_ACCEPTING_ORDERS,
                    StoreErrorCode.NOT_ACCEPTING_ORDERS.message(), Map.of("storeStatus", store.status()));
        }

        List<LineDraft> drafts = buildDrafts(store, request.items());

        int subtotal = 0;
        for (LineDraft draft : drafts) {
            subtotal += LinePricing.lineAmount(draft.unitPrice(), draft.quantity());
        }

        String orderCode = OrderCodeGenerator.generate();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(paymentExpireMinutes * 60);

        Order order = Order.create(orderCode, memberId, store.id(), store.schoolId(), subtotal, 0,
                request.requestMemo(), now, store.commissionRate(), idempotencyKey, requestHash, expiresAt);
        order = orderRepository.save(order);
        orderStatusHistoryRepository.save(OrderStatusHistory.created(order.getId(), Order.STATUS_PENDING_PAYMENT, memberId));

        List<OrderCreateResponse.LineItem> echoLines = persistLinesAndBuildEcho(order.getId(), drafts);

        PaymentAttemptView payment = paymentPreparationApi.prepareFirstAttempt(orderCode, order.getTotalAmount());
        String orderName = drafts.size() == 1
                ? drafts.get(0).menu().name()
                : drafts.get(0).menu().name() + " 외 " + (drafts.size() - 1) + "건";

        OrderCreateResponse response = new OrderCreateResponse(order.getId(), orderCode, order.getStatus(),
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.of("+09:00")), echoLines,
                order.getSubtotalAmount(), order.getDiscountAmount(), order.getTotalAmount(),
                new OrderCreateResponse.PaymentInfo(payment.attemptNo(), payment.pgProvider(), payment.pgOrderId(),
                        orderName, order.getTotalAmount(), payment.clientKey()));
        return new Result(response, true);
    }

    private record LineDraft(MenuItemView menu, int quantity, int unitPrice, MenuSelectionValidator.Result selection) {
    }

    private List<LineDraft> buildDrafts(StoreView store, List<OrderCreateRequest.Item> items) {
        List<LineDraft> drafts = new ArrayList<>();
        List<Map<String, Object>> soldOut = new ArrayList<>();

        for (OrderCreateRequest.Item item : items) {
            MenuItemView menu = menuQueryApi.findMenuItem(store.id(), item.menuId())
                    .orElseThrow(() -> new BusinessException(OrderErrorCode.MENU_NOT_FOUND));

            if (menu.soldOut()) {
                soldOut.add(Map.of("menuId", menu.id(), "name", menu.name()));
                continue;
            }
            if (item.quantity() == null || item.quantity() < 1 || item.quantity() > 20) {
                throw BusinessException.withData(OrderErrorCode.QUANTITY_LIMIT_EXCEEDED,
                        OrderErrorCode.QUANTITY_LIMIT_EXCEEDED.message(), Map.of("maxQuantityPerItem", 20, "maxItems", 20));
            }

            MenuSelectionValidator.Result selection;
            try {
                selection = MenuSelectionValidator.validate(menu,
                        item.optionItemIds() == null ? List.of() : item.optionItemIds());
            } catch (InvalidOptionSelectionException e) {
                throw BusinessException.withData(OrderErrorCode.INVALID_OPTION_SELECTION,
                        OrderErrorCode.INVALID_OPTION_SELECTION.message(),
                        Map.of("menuId", menu.id(), "optionGroup", e.optionGroupName() == null ? "" : e.optionGroupName()));
            }
            int unitPrice = LinePricing.unitPrice(menu.basePrice(), selection.optionPriceDeltaSum());
            drafts.add(new LineDraft(menu, item.quantity(), unitPrice, selection));
        }

        if (!soldOut.isEmpty()) {
            throw BusinessException.withData(OrderErrorCode.SOLD_OUT, OrderErrorCode.SOLD_OUT.message(),
                    Map.of("soldOutItems", soldOut));
        }
        return drafts;
    }

    private List<OrderCreateResponse.LineItem> persistLinesAndBuildEcho(Long orderId, List<LineDraft> drafts) {
        List<OrderCreateResponse.LineItem> echo = new ArrayList<>();
        for (LineDraft draft : drafts) {
            int lineAmount = LinePricing.lineAmount(draft.unitPrice(), draft.quantity());
            OrderLine line = OrderLine.of(orderId, draft.menu().id(), draft.menu().name(),
                    draft.menu().basePrice(), draft.unitPrice(), draft.quantity());
            line = orderLineRepository.save(line);

            List<String> optionNames = new ArrayList<>();
            for (MenuSelectionValidator.SelectedOption option : draft.selection().selectedOptions()) {
                orderLineOptionRepository.save(OrderLineOption.of(line.getId(), option.optionItemId(),
                        option.groupName(), option.itemName(), option.priceDelta()));
                optionNames.add(option.itemName());
            }
            echo.add(new OrderCreateResponse.LineItem(draft.menu().name(), optionNames, draft.unitPrice(),
                    draft.quantity(), lineAmount));
        }
        return echo;
    }

    /** 같은 Idempotency-Key로 재요청 — 같은 해시면 기존 주문을 그대로 돌려주고, 다르면 OD010. */
    private OrderCreateResponse replay(Order order, String requestHash) {
        if (!order.getRequestHash().equals(requestHash)) {
            throw new BusinessException(OrderErrorCode.IDEMPOTENCY_CONFLICT);
        }
        List<OrderLine> lines = orderLineRepository.findByOrderId(order.getId());
        List<OrderCreateResponse.LineItem> echoLines = lines.stream()
                .map(line -> new OrderCreateResponse.LineItem(
                        line.getMenuNameSnapshot(),
                        orderLineOptionRepository.findByOrderLineId(line.getId()).stream()
                                .map(OrderLineOption::getOptionItemNameSnapshot).toList(),
                        line.getUnitPrice(), line.getQuantity(), line.getLineAmount()))
                .toList();

        // 가짜 어댑터는 orderCode만으로 결정되는 순수 함수라 재계산해도 같은 값이 나온다.
        PaymentAttemptView payment = paymentPreparationApi.prepareFirstAttempt(order.getOrderCode(), order.getTotalAmount());
        String orderName = lines.isEmpty() ? "" : lines.size() == 1
                ? lines.get(0).getMenuNameSnapshot()
                : lines.get(0).getMenuNameSnapshot() + " 외 " + (lines.size() - 1) + "건";

        return new OrderCreateResponse(order.getId(), order.getOrderCode(), order.getStatus(),
                OffsetDateTime.ofInstant(order.getExpiresAt(), ZoneOffset.of("+09:00")), echoLines,
                order.getSubtotalAmount(), order.getDiscountAmount(), order.getTotalAmount(),
                new OrderCreateResponse.PaymentInfo(payment.attemptNo(), payment.pgProvider(), payment.pgOrderId(),
                        orderName, order.getTotalAmount(), payment.clientKey()));
    }
}
