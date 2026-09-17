package com.coffeul.menu.infrastructure;

import com.coffeul.menu.domain.OptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OptionItemRepository extends JpaRepository<OptionItem, Long> {

    List<OptionItem> findByOptionGroupIdInOrderBySortOrder(List<Long> optionGroupIds);
}
