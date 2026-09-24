package com.coffeul.order.application;

import com.coffeul.order.api.OrderExpiredEvent;
import com.coffeul.order.domain.InvalidOrderTransitionException;
import com.coffeul.order.domain.Order;
import com.coffeul.order.domain.OrderStatusHistory;
import com.coffeul.order.infrastructure.OrderRepository;
import com.coffeul.order.infrastructure.OrderStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 결제 대기 만료 (rules.py JOBS: 1분마다, PENDING_PAYMENT · expires_at 지남 → EXPIRED + 이력 → OrderExpired).
 *
 * <p><b>알려진 한계</b>: 원본 조건은 "UNKNOWN 시도 없음 · 마지막 READY 시도 후 10분 지남"까지 포함하지만,
 * payment 모듈이 아직 결제 시도를 {@code payment} 테이블에 영속화하지 않는다 (PaymentPreparationApi 참고 —
 * merchant_pg 실데이터가 없어서). 그래서 지금은 이 두 조건이 항상 공진(vacuously true)이라 검사하지 않는다.
 * MS-27(결제 승인)이 실제 결제 시도를 저장하기 시작하면 이 조건을 마저 채운다.
 */
@Component
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);
    private static final String ACTOR_SYSTEM = "SYSTEM";

    /** 한 번 실행에서 도는 배치 수의 상한 (VerificationCleanupJob과 같은 패턴). */
    private static final int MAX_BATCHES = 100;

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    // expireOne을 건별 트랜잭션으로 나누려면 프록시를 거쳐 호출해야 한다 — 같은 객체 안에서 this.expireOne(...)로
    // 부르면 자기 호출이라 프록시(따라서 @Transactional)를 타지 않는다. @Lazy 자가 주입으로 프록시를 얻어 통해서 부른다.
    private final OrderExpiryJob self;

    public OrderExpiryJob(OrderRepository orderRepository,
                           OrderStatusHistoryRepository orderStatusHistoryRepository,
                           ApplicationEventPublisher eventPublisher,
                           @Lazy OrderExpiryJob self) {
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.eventPublisher = eventPublisher;
        this.self = self;
    }

    @Scheduled(cron = "${coffeul.order.expire-cron:0 * * * * *}", zone = "${coffeul.business-zone:Asia/Seoul}")
    public void run() {
        int expired = expireOverdue(Instant.now());
        log.info("결제 대기 만료 처리 완료 — {}건", expired);
    }

    /** 테스트에서 시각을 직접 넘길 수 있게 분리. 한 건씩 트랜잭션을 나눠 한 건 실패가 나머지를 막지 않게 한다 (JOB_RULES). */
    public int expireOverdue(Instant now) {
        int expired = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Order> targets = orderRepository.findTop100ByStatusAndExpiresAtLessThan(Order.STATUS_PENDING_PAYMENT, now);
            if (targets.isEmpty()) {
                return expired;
            }
            for (Order target : targets) {
                if (self.expireOne(target.getId(), now)) {
                    expired++;
                }
            }
        }
        log.warn("결제 대기 만료 처리가 한 번에 {}건 상한에 걸렸다 — 남은 건은 다음 실행에서 처리한다", expired);
        return expired;
    }

    /**
     * 배치 조회와 실제 처리 사이에 다른 트랜잭션이 먼저 상태를 바꿨을 수 있어(예: 나중에 붙을 MS-27
     * 결제 승인), findByIdForUpdate로 다시 잠그고 현재 상태를 확인한 뒤에만 만료시킨다.
     */
    @Transactional
    public boolean expireOne(Long orderId, Instant now) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null) {
            return false;
        }
        try {
            order.expire(now);
        } catch (InvalidOrderTransitionException e) {
            return false;
        }
        orderRepository.save(order);
        orderStatusHistoryRepository.save(OrderStatusHistory.transitioned(
                order.getId(), Order.STATUS_PENDING_PAYMENT, Order.STATUS_EXPIRED, ACTOR_SYSTEM, null, null));
        eventPublisher.publishEvent(new OrderExpiredEvent(order.getId(), order.getMemberId()));
        return true;
    }
}
