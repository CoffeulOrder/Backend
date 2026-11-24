package com.coffeul.menu.application;

import com.coffeul.auth.api.AuthUser;
import com.coffeul.auth.api.StaffCredentialPort;
import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.menu.domain.MenuItem;
import com.coffeul.menu.domain.OptionItem;
import com.coffeul.menu.infrastructure.MenuItemRepository;
import com.coffeul.menu.infrastructure.OptionItemRepository;
import com.coffeul.menu.presentation.StaffMenuResponse;
import com.coffeul.store.api.StoreAccessPolicy;
import com.coffeul.store.api.StoreErrorCode;
import com.coffeul.store.api.StoreQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class StaffMenuService {
    private final MenuItemRepository menus;
    private final OptionItemRepository options;
    private final MenuOptionReader optionReader;
    private final StaffCredentialPort staffAccounts;
    private final StoreQueryApi stores;
    private final StoreAccessPolicy access;

    public StaffMenuService(MenuItemRepository menus, OptionItemRepository options, MenuOptionReader optionReader,
                            StaffCredentialPort staffAccounts, StoreQueryApi stores, StoreAccessPolicy access) {
        this.menus = menus;
        this.options = options;
        this.optionReader = optionReader;
        this.staffAccounts = staffAccounts;
        this.stores = stores;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<StaffMenuResponse> listMenus(AuthUser staff, Long storeId) {
        requireAccess(staff, storeId);
        List<MenuItem> items = menus.findByStoreIdOrderBySortOrderAscIdAsc(storeId);
        var groupsByMenu = optionReader.findGroupsByMenuIds(items.stream().map(MenuItem::getId).toList());
        return items.stream().map(menu -> new StaffMenuResponse(menu.getId(), menu.getName(), menu.getStatus(),
                menu.isSoldOut(), groupsByMenu.getOrDefault(menu.getId(), List.of()).stream()
                .map(entry -> new StaffMenuResponse.OptionGroup(entry.group().getId(), entry.group().getName(),
                        entry.options().stream().map(option -> new StaffMenuResponse.Option(option.getId(),
                                option.getName(), option.isSoldOut())).toList()))
                .toList())).toList();
    }

    @Transactional
    public MenuItem changeMenuSoldOut(AuthUser staff, Long storeId, Long menuId, boolean soldOut) {
        requireAccess(staff, storeId);
        MenuItem menu = menus.findForUpdate(storeId, menuId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        menu.changeSoldOut(soldOut);
        return menu;
    }

    @Transactional
    public OptionItem changeOptionSoldOut(AuthUser staff, Long storeId, Long optionItemId, boolean soldOut) {
        requireAccess(staff, storeId);
        OptionItem option = options.findForUpdate(storeId, optionItemId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        option.changeSoldOut(soldOut);
        return option;
    }

    private void requireAccess(AuthUser staff, Long storeId) {
        if (staff.type() != AuthUser.SubjectType.STAFF
                || (staff.role() != AuthUser.Role.OWNER && staff.role() != AuthUser.Role.STAFF)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        staffAccounts.findById(staff.id())
                .filter(account -> account.active() && account.role().equals(staff.role().name())
                        && Objects.equals(account.merchantId(), staff.merchantId()))
                .orElseThrow(() -> new BusinessException(StoreErrorCode.FORBIDDEN));
        if (stores.findById(storeId).isEmpty()) {
            throw new BusinessException(StoreErrorCode.NOT_FOUND);
        }
        access.check(staff, storeId);
    }
}
