package com.coffeul.store.application;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.store.api.StoreAccessPolicy;
import com.coffeul.store.api.StoreActiveOrderCountPort;
import com.coffeul.store.api.StoreErrorCode;
import com.coffeul.store.domain.Store;
import com.coffeul.store.infrastructure.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/** MS-8(영업 상태 변경). */
@Service
public class StoreStatusService {

    private static final Set<String> VALID_STATUSES = Set.of(Store.STATUS_OPEN, Store.STATUS_PAUSED, Store.STATUS_CLOSED);

    private final StoreRepository storeRepository;
    private final StoreAccessPolicy storeAccessPolicy;
    private final StoreActiveOrderCountPort storeActiveOrderCountPort;

    public StoreStatusService(StoreRepository storeRepository,
                               StoreAccessPolicy storeAccessPolicy,
                               StoreActiveOrderCountPort storeActiveOrderCountPort) {
        this.storeRepository = storeRepository;
        this.storeAccessPolicy = storeAccessPolicy;
        this.storeActiveOrderCountPort = storeActiveOrderCountPort;
    }

    @Transactional
    public Store changeStatus(AuthUser staff, Long storeId, String status, boolean force) {
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException(StoreErrorCode.INVALID_STATUS);
        }
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(StoreErrorCode.NOT_FOUND));
        storeAccessPolicy.check(staff, storeId);

        if (Store.STATUS_CLOSED.equals(status) && !force) {
            long activeOrderCount = storeActiveOrderCountPort.countActiveOrders(storeId);
            if (activeOrderCount > 0) {
                throw BusinessException.withData(StoreErrorCode.ACTIVE_ORDERS_REMAIN,
                        StoreErrorCode.ACTIVE_ORDERS_REMAIN.message(), Map.of("activeOrderCount", activeOrderCount));
            }
        }

        store.changeStatus(status);
        return storeRepository.save(store);
    }
}
