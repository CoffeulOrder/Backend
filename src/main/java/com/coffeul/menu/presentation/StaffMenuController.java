package com.coffeul.menu.presentation;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.common.response.ApiResponse;
import com.coffeul.menu.application.StaffMenuService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** MS-12·13·14 사장님/배정 직원의 품절 관리. */
@RestController
public class StaffMenuController {
    private final StaffMenuService service;

    public StaffMenuController(StaffMenuService service) {
        this.service = service;
    }

    public record SoldOutRequest(@NotNull Boolean soldOut) { }
    public record MenuSoldOutResponse(Long menuId, boolean soldOut) { }
    public record OptionSoldOutResponse(Long optionItemId, boolean soldOut) { }

    @GetMapping("/api/v1/staff/stores/{storeId}/menus")
    public ApiResponse<List<StaffMenuResponse>> menus(AuthUser staff, @PathVariable Long storeId) {
        return ApiResponse.success("메뉴를 불러왔어요.", service.listMenus(staff, storeId));
    }

    @PatchMapping("/api/v1/staff/stores/{storeId}/menus/{menuId}/sold-out")
    public ApiResponse<MenuSoldOutResponse> menuSoldOut(AuthUser staff, @PathVariable Long storeId,
            @PathVariable Long menuId, @Valid @RequestBody SoldOutRequest request) {
        var menu = service.changeMenuSoldOut(staff, storeId, menuId, request.soldOut());
        return ApiResponse.success("품절 상태를 변경했어요.", new MenuSoldOutResponse(menu.getId(), menu.isSoldOut()));
    }

    @PatchMapping("/api/v1/staff/stores/{storeId}/option-items/{optionItemId}/sold-out")
    public ApiResponse<OptionSoldOutResponse> optionSoldOut(AuthUser staff, @PathVariable Long storeId,
            @PathVariable Long optionItemId, @Valid @RequestBody SoldOutRequest request) {
        var option = service.changeOptionSoldOut(staff, storeId, optionItemId, request.soldOut());
        return ApiResponse.success("품절 상태를 변경했어요.", new OptionSoldOutResponse(option.getId(), option.isSoldOut()));
    }
}
