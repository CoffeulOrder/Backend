package com.coffeul.order.infrastructure;

import com.coffeul.order.domain.OrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    List<OrderLine> findByOrderId(Long orderId);

    /** 고객 조회(MS-17 · MS-18)는 담은 순서 그대로 보여줘야 해서 id 순으로 고정한다. */
    List<OrderLine> findByOrderIdOrderByIdAsc(Long orderId);
}
