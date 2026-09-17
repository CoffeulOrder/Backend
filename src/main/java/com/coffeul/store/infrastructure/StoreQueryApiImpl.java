package com.coffeul.store.infrastructure;

import com.coffeul.store.api.StoreQueryApi;
import com.coffeul.store.api.StoreView;
import com.coffeul.store.domain.Merchant;
import com.coffeul.store.domain.Store;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class StoreQueryApiImpl implements StoreQueryApi {

    private final StoreRepository storeRepository;
    private final MerchantRepository merchantRepository;

    public StoreQueryApiImpl(StoreRepository storeRepository, MerchantRepository merchantRepository) {
        this.storeRepository = storeRepository;
        this.merchantRepository = merchantRepository;
    }

    @Override
    public Optional<StoreView> findById(Long storeId) {
        return storeRepository.findById(storeId).map(s -> {
            BigDecimal commissionRate = merchantRepository.findById(s.getMerchantId())
                    .map(Merchant::getCommissionRate)
                    .orElse(BigDecimal.ZERO);
            return new StoreView(s.getId(), s.getName(), s.getStatus(), s.getSchoolId(), commissionRate);
        });
    }
}
