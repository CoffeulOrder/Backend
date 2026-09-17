package com.coffeul.order.infrastructure;

import com.coffeul.order.domain.OrderLineOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderLineOptionRepository extends JpaRepository<OrderLineOption, Long> {

    List<OrderLineOption> findByOrderLineId(Long orderLineId);
}
