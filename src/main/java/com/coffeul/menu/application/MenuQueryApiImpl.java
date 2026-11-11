package com.coffeul.menu.application;

import com.coffeul.menu.api.MenuItemView;
import com.coffeul.menu.api.MenuQueryApi;
import com.coffeul.menu.domain.MenuItem;
import com.coffeul.menu.infrastructure.MenuItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class MenuQueryApiImpl implements MenuQueryApi {

    private final MenuItemRepository menuItemRepository;
    private final MenuOptionReader menuOptionReader;

    public MenuQueryApiImpl(MenuItemRepository menuItemRepository,
                             MenuOptionReader menuOptionReader) {
        this.menuItemRepository = menuItemRepository;
        this.menuOptionReader = menuOptionReader;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MenuItemView> findMenuItem(Long storeId, Long menuId) {
        return menuItemRepository.findById(menuId)
                .filter(item -> item.getStoreId().equals(storeId))
                .map(this::toView);
    }

    private MenuItemView toView(MenuItem item) {
        List<MenuItemView.OptionGroupView> groupViews = menuOptionReader.findGroups(item.getId()).stream()
                .map(entry -> {
                    var group = entry.group();
                    return new MenuItemView.OptionGroupView(
                            group.getId(), group.getName(), group.isRequired(), group.getMinSelect(), group.getMaxSelect(),
                            entry.options().stream()
                                    .map(option -> new MenuItemView.OptionItemView(
                                            option.getId(), option.getOptionGroupId(), option.getName(),
                                            option.getPriceDelta(), option.isSoldOut()))
                                    .toList());
                })
                .toList();

        return new MenuItemView(item.getId(), item.getStoreId(), item.getName(), item.getBasePrice(),
                item.isSoldOut(), groupViews);
    }
}
