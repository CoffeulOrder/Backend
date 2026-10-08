package com.coffeul.order.infrastructure;

import com.coffeul.order.domain.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    long countByMemberIdAndStatusNotIn(Long memberId, Collection<String> excludedStatuses);

    long countByStoreIdAndStatusIn(Long storeId, Collection<String> includedStatuses);

    /**
     * 상태 전이(MS-22~26 · 결제 대기 만료 작업) 전용 조회. 같은 주문에 동시에 들어온 요청을
     * {@code SELECT ... FOR UPDATE}로 한 줄로 세운다 — 뒤에 걸린 요청은 앞선 트랜잭션이 커밋할 때까지
     * 블로킹된 뒤 "그새 바뀐" 최신 상태를 그대로 읽으므로, rules.py의 조건부 UPDATE(WHERE status = :from)와
     * 같은 보장("동시에 다른 전이 → 한쪽만 성공")을 낙관적 재시도 없이 얻는다. 태블릿 두 대가 거의 동시에
     * 버튼을 누르는 정도의 동시성이라 행 잠금 대기가 문제되지 않는다 (JOB_RULES: 서버 1대 가정).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    List<Order> findTop100ByStatusAndExpiresAtLessThan(String status, Instant threshold);

    /** MS-20: 접수 ~ 픽업 대기 주문을 접수순으로. */
    List<Order> findByStoreIdAndStatusInOrderByPlacedAtAsc(Long storeId, Collection<String> statuses);

    /** MS-21: 영업일 하루치 전체 주문. */
    List<Order> findByStoreIdAndBusinessDate(Long storeId, LocalDate businessDate);
}
