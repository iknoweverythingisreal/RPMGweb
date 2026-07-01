package com.rpmedia.backend.repository;

import com.rpmedia.backend.dto.EquipmentAvailabilityRow;
import com.rpmedia.backend.model.Item;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

  Optional<Item> findByName(String name);

  List<Item> findByNameContainingIgnoreCase(String name);

  List<Item> findByCategoryIgnoreCase(String category);

  // (เมธอดเดิมของคุณ เช่น findFirstBy... ใส่ไว้ได้ตามเดิม)
  Optional<Item> findFirstByCategoryIgnoreCaseAndBrandIgnoreCaseAndModelIgnoreCaseAndDescriptionIgnoreCaseAndUomIgnoreCase(
      String category, String brand, String model, String description, String uom);

  @Query(value = """
      SELECT
        i.id                 AS id,
        i.name               AS name,
        i.category           AS category,
        i.brand              AS brand,
        i.model              AS model,
        i.uom                AS uom,
        i.total_quantity     AS totalQuantity,
        GREATEST(
          i.total_quantity
          - COALESCE(SUM(
              CASE
                WHEN e.start_date <= :endDate
                 AND COALESCE(ei.return_date, e.end_date) >= :startDate
                 AND COALESCE(ei.status, 'CONFIRMED') <> 'CANCELLED'
                 AND COALESCE(e.status, 'CONFIRMED') <> 'CANCELLED'
                THEN ei.allocated_quantity
                ELSE 0
              END
            ), 0),
          0
        )                   AS available
      FROM items i
      LEFT JOIN event_items ei ON ei.item_id = i.id
      LEFT JOIN events e       ON e.id = ei.event_id
      WHERE (:category IS NULL OR i.category = :category)
        AND (
             :q IS NULL OR :q = ''
          OR LOWER(i.name)  LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(i.brand) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(i.model) LIKE LOWER(CONCAT('%', :q, '%'))
        )
      GROUP BY i.id, i.name, i.category, i.brand, i.model, i.uom, i.total_quantity
      ORDER BY i.name
      """, nativeQuery = true)
  List<EquipmentAvailabilityRow> findAvailability(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("category") String category,
      @Param("q") String q,
      Pageable pageable // Spring จะใส่ LIMIT/OFFSET ให้เอง
  );

  @org.springframework.data.jpa.repository.Query(nativeQuery = true, value = """
        SELECT GREATEST(
          i.total_quantity
          - COALESCE(SUM(
              CASE
               WHEN e.id <> :eventId
                AND e.start_date <= :endDate
                AND COALESCE(ei.return_date, e.end_date) >= :startDate
                AND COALESCE(ei.status,'CONFIRMED') <> 'CANCELLED'
                AND COALESCE(e.status,'CONFIRMED') <> 'CANCELLED'
              THEN ei.allocated_quantity ELSE 0 END
            ),0),
          0
        ) AS available
        FROM items i
        LEFT JOIN event_items ei ON ei.item_id=i.id
        LEFT JOIN events e ON e.id=ei.event_id
        WHERE i.id=:itemId
      """)
  Integer availableForItem(
      @org.springframework.data.repository.query.Param("itemId") Long itemId,
      @org.springframework.data.repository.query.Param("eventId") Long eventId,
      @org.springframework.data.repository.query.Param("startDate") java.time.LocalDate startDate,
      @org.springframework.data.repository.query.Param("endDate") java.time.LocalDate endDate);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("SELECT i FROM Item i WHERE i.id = :id")
  java.util.Optional<Item> findByIdWithLock(@org.springframework.data.repository.query.Param("id") Long id);

}
