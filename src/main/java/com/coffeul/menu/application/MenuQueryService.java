package com.coffeul.menu.application;

import com.coffeul.common.error.BusinessException;
import com.coffeul.common.error.CommonErrorCode;
import com.coffeul.menu.domain.Category;
import com.coffeul.menu.domain.MenuItem;
import com.coffeul.menu.domain.OptionGroup;
import com.coffeul.menu.infrastructure.CategoryRepository;
import com.coffeul.menu.infrastructure.MenuItemRepository;
import com.coffeul.menu.infrastructure.OptionGroupRepository;
import com.coffeul.menu.presentation.CategoryResponse;
import com.coffeul.menu.presentation.MenuDetailResponse;
import com.coffeul.menu.presentation.MenuResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** MS-9(카테고리 목록) · MS-10(메뉴 목록) · MS-11(메뉴 상세) 조회 서비스. */
@Service
@Transactional(readOnly = true)
public class MenuQueryService {

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final MenuOptionReader menuOptionReader;
    private final String cdnBaseUrl;

    public MenuQueryService(CategoryRepository categoryRepository,
                             MenuItemRepository menuItemRepository,
                             OptionGroupRepository optionGroupRepository,
                             MenuOptionReader menuOptionReader,
                             @Value("${coffeul.cdn.base-url:}") String cdnBaseUrl) {
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.menuOptionReader = menuOptionReader;
        this.cdnBaseUrl = cdnBaseUrl;
    }

    public List<CategoryResponse> listCategories(Long storeId) {
        return categoryRepository.findByStoreIdOrderBySortOrder(storeId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<MenuResponse> listMenus(Long storeId, Long categoryId) {
        List<MenuItem> items = categoryId == null
                ? menuItemRepository.findByStoreIdOrderBySortOrder(storeId)
                : menuItemRepository.findByStoreIdAndCategoryId(storeId, categoryId);

        Set<Long> idsWithOptions = items.isEmpty()
                ? Set.of()
                : optionGroupRepository.findByMenuItemIdInOrderBySortOrder(items.stream().map(MenuItem::getId).toList())
                        .stream().map(OptionGroup::getMenuItemId).collect(java.util.stream.Collectors.toSet());

        return items.stream().map(item -> toResponse(item, idsWithOptions.contains(item.getId()))).toList();
    }

    public MenuDetailResponse detail(Long storeId, Long menuId) {
        MenuItem item = menuItemRepository.findById(menuId)
                .filter(menu -> menu.getStoreId().equals(storeId) && "ACTIVE".equals(menu.getStatus()))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        List<MenuDetailResponse.OptionGroup> groups = menuOptionReader.findGroups(menuId).stream()
                .map(entry -> {
                    OptionGroup group = entry.group();
                    List<MenuDetailResponse.Option> options = entry.options().stream()
                            .map(option -> new MenuDetailResponse.Option(option.getId(), option.getName(),
                                    option.getPriceDelta(), option.isDefault(), option.isSoldOut()))
                            .toList();
                    return new MenuDetailResponse.OptionGroup(group.getId(), group.getName(), group.getOptionType(),
                            group.isRequired(), group.getMinSelect(), group.getMaxSelect(), options);
                })
                .toList();
        return new MenuDetailResponse(item.getId(), item.getName(), item.getDescription(), item.getBasePrice(),
                imageUrlOf(item), item.isSoldOut(), groups);
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSortOrder());
    }

    private MenuResponse toResponse(MenuItem item, boolean hasOptions) {
        return new MenuResponse(item.getId(), item.getName(), item.getDescription(), item.getBasePrice(),
                imageUrlOf(item), item.isSoldOut(), item.isNew(), item.isEvent(), hasOptions);
    }

    private String imageUrlOf(MenuItem item) {
        return item.getImageKey() == null || item.getImageKey().isBlank()
                ? null
                : cdnBaseUrl + "/" + item.getImageKey();
    }
}
