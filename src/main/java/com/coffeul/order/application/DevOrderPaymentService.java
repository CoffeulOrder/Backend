package com.coffeul.order.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.member.api.MemberStatusApi;
import com.coffeul.menu.api.MenuQueryApi;
import com.coffeul.order.domain.*;
import com.coffeul.order.infrastructure.*;
import com.coffeul.payment.api.FakePaymentApi;
import com.coffeul.payment.api.PaymentErrorCode;
import com.coffeul.store.api.StoreErrorCode;
import com.coffeul.store.api.StoreQueryApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** 로컬/개발 서버에서만 사용하는 가짜 결제 확정. 실결제 승인(MS-27)이 아니다. */
@Service
@Profile("dev & !prod & !production")
@ConditionalOnProperty(name = "coffeul.dev.payment-flow-enabled", havingValue = "true")
public class DevOrderPaymentService {
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository histories;
    private final OrderDailyCounterRepository counters;
    private final OrderLineRepository lines;
    private final OrderLineOptionRepository options;
    private final MemberStatusApi members;
    private final StoreQueryApi stores;
    private final MenuQueryApi menus;
    private final FakePaymentApi payments;
    private final ZoneId businessZone;

    public DevOrderPaymentService(OrderRepository orders, OrderStatusHistoryRepository histories,
                                  OrderDailyCounterRepository counters, OrderLineRepository lines,
                                  OrderLineOptionRepository options, MemberStatusApi members,
                                  StoreQueryApi stores, MenuQueryApi menus, FakePaymentApi payments,
                                  @Value("${coffeul.business-zone:Asia/Seoul}") String businessZone) {
        this.orders = orders;
        this.histories = histories;
        this.counters = counters;
        this.lines = lines;
        this.options = options;
        this.members = members;
        this.stores = stores;
        this.menus = menus;
        this.payments = payments;
        this.businessZone = ZoneId.of(businessZone);
    }

    public record Result(Order order, FakePaymentApi.State payment) { }

    @Transactional
    public Result confirm(Long memberId, Long orderId, String pgOrderId) {
        Order order = orders.findByIdForUpdate(orderId)
                .filter(found -> found.getMemberId().equals(memberId))
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
        if (!members.isActive(memberId)) {
            throw new BusinessException(OrderErrorCode.MEMBER_NOT_USABLE);
        }
        FakePaymentApi.State state = payments.findAttempt(orderId, pgOrderId);
        // 승인 뒤 수락·제조 등이 진행됐어도 재전송은 현재 주문 상태를 그대로 돌려준다.
        if ("APPROVED".equals(state.status()) && !Order.STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
            return new Result(order, state);
        }
        if (!Order.STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
            throw BusinessException.withData(OrderErrorCode.INVALID_TRANSITION,
                    OrderErrorCode.INVALID_TRANSITION.message(), Map.of("currentStatus", order.getStatus()));
        }
        Instant now = Instant.now();
        if (!order.getExpiresAt().isAfter(now)) {
            throw new BusinessException(PaymentErrorCode.ORDER_EXPIRED);
        }
        var store = stores.findById(order.getStoreId())
                .orElseThrow(() -> new BusinessException(StoreErrorCode.NOT_FOUND));
        if (!store.isOpen()) {
            throw BusinessException.withData(StoreErrorCode.NOT_ACCEPTING_ORDERS,
                    StoreErrorCode.NOT_ACCEPTING_ORDERS.message(), Map.of("storeStatus", store.status()));
        }
        validateAvailability(order);
        state = payments.approve(orderId, pgOrderId, order.getTotalAmount(), now);
        LocalDate businessDate = LocalDate.ofInstant(now, businessZone);
        order.place(now, businessDate, counters.next(order.getStoreId(), businessDate));
        orders.save(order);
        histories.save(OrderStatusHistory.transitioned(orderId, Order.STATUS_PENDING_PAYMENT,
                Order.STATUS_REQUESTED, "SYSTEM", null, "DEV_FAKE_PAYMENT"));
        return new Result(order, state);
    }

    private void validateAvailability(Order order) {
        for (OrderLine line : lines.findByOrderId(order.getId())) {
            var menu = menus.findMenuItem(order.getStoreId(), line.getMenuItemId())
                    .orElseThrow(() -> new BusinessException(OrderErrorCode.MENU_NOT_FOUND));
            if (menu.soldOut()) {
                throw BusinessException.withData(OrderErrorCode.SOLD_OUT, OrderErrorCode.SOLD_OUT.message(),
                        Map.of("soldOutItems", List.of(Map.of("menuId", menu.id(), "name", menu.name()))));
            }
            try {
                MenuSelectionValidator.validate(menu, options.findByOrderLineId(line.getId()).stream()
                        .map(OrderLineOption::getOptionItemId).toList());
            } catch (InvalidOptionSelectionException ex) {
                throw new BusinessException(OrderErrorCode.INVALID_OPTION_SELECTION);
            }
        }
    }
}
