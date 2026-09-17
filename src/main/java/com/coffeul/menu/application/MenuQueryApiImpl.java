package com.coffeul.menu.application;

import com.coffeul.menu.api.MenuItemView;
import com.coffeul.menu.api.MenuQueryApi;
import com.coffeul.menu.domain.MenuItem;
import com.coffeul.menu.domain.OptionGroup;
import com.coffeul.menu.domain.OptionItem;
import com.coffeul.menu.infrastructure.MenuItemRepository;
import com.coffeul.menu.infrastructure.OptionGroupRepository;
import com.coffeul.menu.infrastructure.OptionItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class MenuQueryApiImpl implements MenuQueryApi {

    private final MenuItemRepository menuItemRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final OptionItemRepository optionItemRepository;

    public MenuQueryApiImpl(MenuItemRepository menuItemRepository,
                             OptionGroupRepository optionGroupRepository,
                             OptionItemRepository optionItemRepository) {
        this.menuItemRepository = menuItemRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.optionItemRepository = optionItemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MenuItemView> findMenuItem(Long storeId, Long menuId) {
        return menuItemRepository.findById(menuId)
                .filter(item -> item.getStoreId().equals(storeId))
                .map(this::toView);
    }

    private MenuItemView toView(MenuItem item) {
        List<OptionGroup> groups = optionGroupRepository.findByMenuItemIdOrderBySortOrder(item.getId());
        List<Long> groupIds = groups.stream().map(OptionGroup::getId).toList();
        Map<Long, List<OptionItem>> optionsByGroup = groupIds.isEmpty()
                ? Map.of()
                : optionItemRepository.findByOptionGroupIdInOrderBySortOrder(groupIds).stream()
                        .collect(Collectors.groupingBy(OptionItem::getOptionGroupId));

        List<MenuItemView.OptionGroupView> groupViews = groups.stream()
                .map(g -> new MenuItemView.OptionGroupView(
                        g.getId(), g.getName(), g.isRequired(), g.getMinSelect(), g.getMaxSelect(),
                        optionsByGroup.getOrDefault(g.getId(), List.of()).stream()
                                .map(o -> new MenuItemView.OptionItemView(
                                        o.getId(), o.getOptionGroupId(), o.getName(), o.getPriceDelta(), o.isSoldOut()))
                                .toList()))
                .toList();

        return new MenuItemView(item.getId(), item.getStoreId(), item.getName(), item.getBasePrice(),
                item.isSoldOut(), groupViews);
    }
}
