package com.coffeul.menu.api;

import java.util.Optional;

/** order 모듈이 주문 생성 시 메뉴 · 옵션 가격 · 품절을 조회하는 포트 (SYNC_CALLS: order → menu.api). */
public interface MenuQueryApi {

    Optional<MenuItemView> findMenuItem(Long storeId, Long menuId);
}
