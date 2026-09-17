package com.coffeul.menu.infrastructure;

import com.coffeul.menu.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByStoreIdOrderBySortOrder(Long storeId);

    @Query("select mi from MenuItem mi join MenuItemCategoryLink l on l.id.menuItemId = mi.id "
            + "where mi.storeId = :storeId and l.id.categoryId = :categoryId order by mi.sortOrder")
    List<MenuItem> findByStoreIdAndCategoryId(@Param("storeId") Long storeId, @Param("categoryId") Long categoryId);
}
