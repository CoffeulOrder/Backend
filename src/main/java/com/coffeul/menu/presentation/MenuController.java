package com.coffeul.menu.presentation;

import com.coffeul.common.error.BusinessException;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.menu.application.MenuQueryService;
import com.coffeul.store.api.StoreErrorCode;
import com.coffeul.store.api.StoreQueryApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MS-9 · MS-10 참조 구현. */
@RestController
public class MenuController {

    private final MenuQueryService menuQueryService;
    private final StoreQueryApi storeQueryApi;

    public MenuController(MenuQueryService menuQueryService, StoreQueryApi storeQueryApi) {
        this.menuQueryService = menuQueryService;
        this.storeQueryApi = storeQueryApi;
    }

    @GetMapping("/api/v1/stores/{storeId}/categories")
    public ApiResponse<java.util.List<CategoryResponse>> categories(@PathVariable Long storeId) {
        requireStore(storeId);
        return ApiResponse.success("카테고리를 불러왔어요.", menuQueryService.listCategories(storeId));
    }

    @GetMapping("/api/v1/stores/{storeId}/menus")
    public ApiResponse<java.util.List<MenuResponse>> menus(@PathVariable Long storeId,
                                                             @RequestParam(required = false) Long categoryId) {
        requireStore(storeId);
        return ApiResponse.success("메뉴를 불러왔어요.", menuQueryService.listMenus(storeId, categoryId));
    }

    private void requireStore(Long storeId) {
        if (storeQueryApi.findById(storeId).isEmpty()) {
            throw new BusinessException(StoreErrorCode.NOT_FOUND);
        }
    }
}
