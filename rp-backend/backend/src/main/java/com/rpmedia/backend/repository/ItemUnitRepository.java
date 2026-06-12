package com.rpmedia.backend.repository;

import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.model.ItemUnit;
import com.rpmedia.backend.model.UnitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemUnitRepository extends JpaRepository<ItemUnit, Long> {

        List<ItemUnit> findByItem_Id(Long itemId);

        // RULE 1 – serial belongs to item
        @Query("""
                            SELECT iu
                            FROM ItemUnit iu
                            WHERE iu.item.id = :itemId
                              AND iu.serial IN :serials
                        """)
        List<ItemUnit> findValidSerials(
                        @Param("itemId") Long itemId,
                        @Param("serials") List<String> serials);

        // RULE 2 – serial status must NOT be AVAILABLE
        @Query("""
                            SELECT iu
                            FROM ItemUnit iu
                            WHERE iu.item.id = :itemId
                              AND iu.serial IN :serials
                              AND iu.status <> com.rpmedia.backend.model.UnitStatus.AVAILABLE
                        """)
        List<ItemUnit> findUnavailableSerials(
                        @Param("itemId") Long itemId,
                        @Param("serials") List<String> serials);

        ItemUnit findBySerial(String serial);

        @Modifying
        @Query("update ItemUnit iu set iu.item = :newItem where iu.item.id in :oldItemIds")
        void migrateItem(@Param("oldItemIds") List<Long> oldItemIds, @Param("newItem") Item newItem);
}
