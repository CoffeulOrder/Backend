package com.coffeul.payment.api;

import java.util.Optional;

public interface PortOneMerchantConfigPort {
    Optional<Config> findActiveInicisConfig(Long storeId);

    record Config(Long merchantPgId, String portOneStoreId, String channelKey) { }
}
