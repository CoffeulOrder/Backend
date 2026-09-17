package com.coffeul.store.api;

import java.util.Optional;

/** 다른 모듈이 매장 존재 · 영업 상태를 확인할 때 쓰는 포트. */
public interface StoreQueryApi {

    Optional<StoreView> findById(Long storeId);
}
