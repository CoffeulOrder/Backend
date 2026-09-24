package com.coffeul.store.infrastructure;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.error.BusinessException;
import com.coffeul.store.api.StoreAccessPolicy;
import com.coffeul.store.api.StoreErrorCode;
import com.coffeul.store.domain.StaffAccount;
import com.coffeul.store.domain.Store;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StoreAccessPolicyImpl implements StoreAccessPolicy {

    private final StoreRepository storeRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final StaffStoreRepository staffStoreRepository;

    public StoreAccessPolicyImpl(StoreRepository storeRepository,
                                  StaffAccountRepository staffAccountRepository,
                                  StaffStoreRepository staffStoreRepository) {
        this.storeRepository = storeRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.staffStoreRepository = staffStoreRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void check(AuthUser authUser, Long storeId) {
        switch (authUser.role()) {
            case ADMIN -> {
                // 운영자는 매장 소속과 무관하게 통과.
            }
            case OWNER -> {
                Store store = storeRepository.findById(storeId)
                        .orElseThrow(() -> new BusinessException(StoreErrorCode.NOT_FOUND));
                if (!store.getMerchantId().equals(authUser.merchantId())) {
                    throw new BusinessException(StoreErrorCode.FORBIDDEN);
                }
            }
            case STAFF -> {
                if (!staffStoreRepository.existsByIdStaffAccountIdAndIdStoreId(authUser.id(), storeId)) {
                    throw new BusinessException(StoreErrorCode.FORBIDDEN);
                }
                StaffAccount staff = staffAccountRepository.findById(authUser.id())
                        .orElseThrow(() -> new BusinessException(StoreErrorCode.FORBIDDEN));
                if (!staff.isActive()) {
                    throw new BusinessException(StoreErrorCode.FORBIDDEN);
                }
            }
            case CUSTOMER -> throw new BusinessException(StoreErrorCode.FORBIDDEN);
        }
    }
}
