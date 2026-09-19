package com.coffeul.order.infrastructure;

import com.coffeul.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    long countByMemberIdAndStatusNotIn(Long memberId, Collection<String> excludedStatuses);
}
