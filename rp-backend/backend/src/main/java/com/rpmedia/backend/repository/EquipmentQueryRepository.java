package com.rpmedia.backend.repository;

import com.rpmedia.backend.dto.EquipmentAvailabilityRow;
import com.rpmedia.backend.model.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface EquipmentQueryRepository extends JpaRepository<Item, Long> {

  @Query(value = """
      SELECT
        i.id                      AS id,
        i.name                    AS name,
        i.category                AS category,
        i.brand                   AS brand,
        i.model                   AS model,
        i.uom                     AS uom,
        i.total_quantity          AS totalQuantity,
        GREATEST(
          i.total_quantity
          - COALESCE(SUM(
              CASE
                WHEN e.start_date <= :endDate
                 AND COALESCE(ei.return_date, e.end_date) >= :startDate
                 AND COALESCE(ei.status, 'CONFIRMED') <> 'CANCELLED'
                THEN ei.requested_quantity
                ELSE 0
              END
            ), 0),
          0
        )                         AS available
      FROM items i
      LEFT JOIN event_items ei ON ei.item_id = i.id
      LEFT JOIN events e       ON e.id = ei.event_id
      WHERE (:category IS NULL OR i.category = :category)
        AND (
             :q IS NULL
          OR  :q = ''
          OR  LOWER(i.name)  LIKE LOWER(CONCAT('%', :q, '%'))
          OR  LOWER(i.brand) LIKE LOWER(CONCAT('%', :q, '%'))
          OR  LOWER(i.model) LIKE LOWER(CONCAT('%', :q, '%'))
        )
      GROUP BY i.id, i.name, i.category, i.brand, i.model, i.uom, i.total_quantity
      ORDER BY i.name
      """,
      countQuery = """
      SELECT COUNT(*)
      FROM items i
      WHERE (:category IS NULL OR i.category = :category)
        AND (
             :q IS NULL
          OR  :q = ''
          OR  LOWER(i.name)  LIKE LOWER(CONCAT('%', :q, '%'))
          OR  LOWER(i.brand) LIKE LOWER(CONCAT('%', :q, '%'))
          OR  LOWER(i.model) LIKE LOWER(CONCAT('%', :q, '%'))
        )
      """,
      nativeQuery = true)
  Page<EquipmentAvailabilityRow> findAvailability(
      @Param("startDate") LocalDate startDate,
      @Param("endDate")   LocalDate endDate,
      @Param("category")  String category,
      @Param("q")         String q,
      Pageable pageable
  );
}
