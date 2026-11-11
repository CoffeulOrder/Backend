package com.coffeul.order.infrastructure;

import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public class OrderDailyCounterRepository {
    private final JdbcTemplate jdbc;
    public OrderDailyCounterRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 호출자의 트랜잭션에서 매장·영업일 행을 잠가 주문 사이 픽업번호 중복을 막는다. */
    public short next(Long storeId, LocalDate businessDate) {
        jdbc.update("INSERT INTO order_daily_counter (store_id,business_date,last_pickup_no) VALUES (?,?,1) "
                        + "ON DUPLICATE KEY UPDATE last_pickup_no = last_pickup_no + 1", storeId, businessDate);
        Integer number = jdbc.queryForObject("SELECT last_pickup_no FROM order_daily_counter "
                + "WHERE store_id = ? AND business_date = ? FOR UPDATE", Integer.class, storeId, businessDate);
        if (number == null || number > Short.MAX_VALUE) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "오늘의 픽업 번호 발급 한도를 넘었어요.");
        }
        return number.shortValue();
    }
}
