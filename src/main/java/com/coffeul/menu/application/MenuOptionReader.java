package com.coffeul.menu.application;

import com.coffeul.menu.domain.OptionGroup;
import com.coffeul.menu.domain.OptionItem;
import com.coffeul.menu.infrastructure.OptionGroupRepository;
import com.coffeul.menu.infrastructure.OptionItemRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 메뉴 상세와 주문 검증이 사용하는 옵션 데이터를 조회한다. */
@Component
class MenuOptionReader {

    private final OptionGroupRepository optionGroupRepository;
    private final OptionItemRepository optionItemRepository;

    MenuOptionReader(OptionGroupRepository optionGroupRepository, OptionItemRepository optionItemRepository) {
        this.optionGroupRepository = optionGroupRepository;
        this.optionItemRepository = optionItemRepository;
    }

    List<GroupOptions> findGroups(Long menuId) {
        List<OptionGroup> groups = optionGroupRepository.findByMenuItemIdOrderBySortOrder(menuId);
        if (groups.isEmpty()) {
            return List.of();
        }

        List<Long> groupIds = groups.stream().map(OptionGroup::getId).toList();
        Map<Long, List<OptionItem>> optionsByGroup = optionItemRepository.findByOptionGroupIdInOrderBySortOrder(groupIds)
                .stream()
                .collect(Collectors.groupingBy(OptionItem::getOptionGroupId));

        return groups.stream()
                .map(group -> new GroupOptions(group, optionsByGroup.getOrDefault(group.getId(), List.of())))
                .toList();
    }

    record GroupOptions(OptionGroup group, List<OptionItem> options) {
    }
}
