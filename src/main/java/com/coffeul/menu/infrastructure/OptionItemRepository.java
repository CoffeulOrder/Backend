package com.coffeul.menu.infrastructure;

import com.coffeul.menu.domain.OptionItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OptionItemRepository extends JpaRepository<OptionItem, Long> {

    List<OptionItem> findByOptionGroupIdInOrderBySortOrder(List<Long> optionGroupIds);

    List<OptionItem> findByOptionGroupIdInOrderBySortOrderAscIdAsc(List<Long> optionGroupIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select option from OptionItem option join OptionGroup optionGroup on option.optionGroupId = optionGroup.id "
            + "join MenuItem menu on optionGroup.menuItemId = menu.id "
            + "where menu.storeId = :storeId and option.id = :optionItemId")
    Optional<OptionItem> findForUpdate(@Param("storeId") Long storeId, @Param("optionItemId") Long optionItemId);
}
