package com.coffeul.order.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.menu.api.MenuQueryApi;
import com.coffeul.order.domain.InvalidOptionSelectionException;
import com.coffeul.order.domain.MenuSelectionValidator;
import com.coffeul.order.domain.Order;
import com.coffeul.order.domain.OrderLine;
import com.coffeul.order.domain.OrderLineOption;
import com.coffeul.order.domain.OrderStatusHistory;
import com.coffeul.order.infrastructure.OrderDailyCounterRepository;
import com.coffeul.order.infrastructure.OrderLineOptionRepository;
import com.coffeul.order.infrastructure.OrderLineRepository;
import com.coffeul.order.infrastructure.OrderRepository;
import com.coffeul.order.infrastructure.OrderStatusHistoryRepository;
import com.coffeul.store.api.StoreErrorCode;
import com.coffeul.store.api.StoreQueryApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
class OrderPaymentPlacement {
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository histories;
    private final OrderDailyCounterRepository counters;
    private final OrderLineRepository lines;
    private final OrderLineOptionRepository options;
    private final StoreQueryApi stores;
    private final MenuQueryApi menus;
    private final ZoneId businessZone;

    OrderPaymentPlacement(OrderRepository orders, OrderStatusHistoryRepository histories,
                          OrderDailyCounterRepository counters, OrderLineRepository lines,
                          OrderLineOptionRepository options, StoreQueryApi stores, MenuQueryApi menus,
                          @Value("${coffeul.business-zone:Asia/Seoul}") String businessZone) {
        this.orders = orders;
        this.histories = histories;
        this.counters = counters;
        this.lines = lines;
        this.options = options;
        this.stores = stores;
        this.menus = menus;
        this.businessZone = ZoneId.of(businessZone);
    }

    void validateAvailability(Order order) {
        var store = stores.findById(order.getStoreId())
                .orElseThrow(() -> new BusinessException(StoreErrorCode.NOT_FOUND));
        if (!store.isOpen()) {
            throw BusinessException.withData(StoreErrorCode.NOT_ACCEPTING_ORDERS,
                    StoreErrorCode.NOT_ACCEPTING_ORDERS.message(), Map.of("storeStatus", store.status()));
        }
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

    void place(Order order, Instant now, String reason) {
        LocalDate businessDate = LocalDate.ofInstant(now, businessZone);
        order.place(now, businessDate, counters.next(order.getStoreId(), businessDate));
        orders.save(order);
        histories.save(OrderStatusHistory.transitioned(order.getId(), Order.STATUS_PENDING_PAYMENT,
                Order.STATUS_REQUESTED, "SYSTEM", null, reason));
    }
}
