package com.coffeul.menu.infrastructure;

import com.coffeul.menu.domain.OptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {

    List<OptionGroup> findByMenuItemIdOrderBySortOrder(Long menuItemId);

    List<OptionGroup> findByMenuItemIdInOrderBySortOrder(List<Long> menuItemIds);
}
