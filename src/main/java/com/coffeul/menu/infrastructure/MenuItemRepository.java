package com.coffeul.menu.infrastructure;

import com.coffeul.menu.domain.MenuItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByStoreIdOrderBySortOrder(Long storeId);

    List<MenuItem> findByStoreIdOrderBySortOrderAscIdAsc(Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select menu from MenuItem menu where menu.storeId = :storeId and menu.id = :menuId")
    Optional<MenuItem> findForUpdate(@Param("storeId") Long storeId, @Param("menuId") Long menuId);

    @Query("select mi from MenuItem mi join MenuItemCategoryLink l on l.id.menuItemId = mi.id "
            + "where mi.storeId = :storeId and l.id.categoryId = :categoryId order by mi.sortOrder")
    List<MenuItem> findByStoreIdAndCategoryId(@Param("storeId") Long storeId, @Param("categoryId") Long categoryId);
}
