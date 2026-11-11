package com.coffeul.payment.api;

import java.util.Optional;

/** 개발 시드의 FAKE 설정만 조회하는 역방향 포트. 실제 PG 자격증명은 조회하지 않는다. */
public interface FakeMerchantConfigPort {
    Optional<Config> findActiveFakeConfig(Long storeId);
    record Config(Long merchantPgId) { }
}
