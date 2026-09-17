package com.coffeul.menu.application;

import com.coffeul.menu.domain.Category;
import com.coffeul.menu.domain.MenuItem;
import com.coffeul.menu.domain.OptionGroup;
import com.coffeul.menu.infrastructure.CategoryRepository;
import com.coffeul.menu.infrastructure.MenuItemRepository;
import com.coffeul.menu.infrastructure.OptionGroupRepository;
import com.coffeul.menu.presentation.CategoryResponse;
import com.coffeul.menu.presentation.MenuResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** MS-9(카테고리 목록) · MS-10(메뉴 목록) 조회 서비스. */
@Service
@Transactional(readOnly = true)
public class MenuQueryService {

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final String cdnBaseUrl;

    public MenuQueryService(CategoryRepository categoryRepository,
                             MenuItemRepository menuItemRepository,
                             OptionGroupRepository optionGroupRepository,
                             @Value("${coffeul.cdn.base-url:}") String cdnBaseUrl) {
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.optionGroupRepository = optionGroupRepository;
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

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSortOrder());
    }

    private MenuResponse toResponse(MenuItem item, boolean hasOptions) {
        String imageUrl = item.getImageKey() == null || item.getImageKey().isBlank()
                ? null
                : cdnBaseUrl + "/" + item.getImageKey();
        return new MenuResponse(item.getId(), item.getName(), item.getDescription(), item.getBasePrice(),
                imageUrl, item.isSoldOut(), item.isNew(), item.isEvent(), hasOptions);
    }
}
