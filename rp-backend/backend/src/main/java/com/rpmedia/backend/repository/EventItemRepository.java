package com.rpmedia.backend.repository;

import com.rpmedia.backend.model.EventItem;
import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.model.ItemStatus;
import com.rpmedia.backend.model.OverbookStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EventItemRepository extends JpaRepository<EventItem, Long> {

  List<EventItem> findByEventIdAndItemId(Long eventId, Long itemId);

  boolean existsByEventIdAndItemId(Long eventId, Long itemId);

  List<EventItem> findByEventId(Long eventId);

  @Modifying
  @Query("update EventItem ei set ei.item = :newItem where ei.item.id in :oldItemIds")
  void migrateItem(@Param("oldItemIds") List<Long> oldItemIds, @Param("newItem") Item newItem);

  // ==========================
  // Availability overlap sum
  // ==========================
  @Query("""
      select coalesce(sum(ei.allocatedQuantity), 0)
      from EventItem ei
      where ei.item.id = :itemId
        and (:excludeEventId is null or ei.event.id <> :excludeEventId)
        and ei.event.startDate <= :toDate
        and ei.event.endDate >= :fromDate
        and (ei.status is null or (ei.status <> 'CANCELLED' and ei.status <> 'RETURNED'))
      """)
  Long sumAllocatedOverlap(
      @Param("itemId") Long itemId,
      @Param("excludeEventId") Long excludeEventId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

  @Query("""
      select ei
      from EventItem ei
      where ei.item.id = :itemId
        and ei.event.startDate <= :toDate
        and ei.event.endDate >= :fromDate
        and (ei.status is null or (ei.status <> 'CANCELLED' and ei.status <> 'RETURNED'))
      """)
  List<EventItem> findOverlappingUsage(
      @Param("itemId") Long itemId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

  List<EventItem> findByEventIdAndOverbookStatus(Long eventId, OverbookStatus status);

  long countByStatus(ItemStatus status);

  long countByOverbookStatus(OverbookStatus status);

  List<EventItem> findByOverbookStatus(OverbookStatus status);

  @Query("""
      SELECT ei FROM EventItem ei
      WHERE ei.overbookStatus = 'PENDING'
         OR ei.status = 'PENDING_RENT'
      """)
  List<EventItem> findPendingRentals();

  @Query("""
      select ei.item.id as itemId, sum(ei.allocatedQuantity) as allocated
      from EventItem ei
      where (:excludeEventId is null or ei.event.id <> :excludeEventId)
        and ei.event.startDate <= :toDate
        and ei.event.endDate >= :fromDate
        and (ei.status is null or (ei.status <> 'CANCELLED' and ei.status <> 'RETURNED'))
      group by ei.item.id
      """)
  List<Object[]> sumAllocatedOverlapBulk(
      @Param("excludeEventId") Long excludeEventId,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

  @Query("""
      SELECT iu.serial
      FROM EventItemUnit eiu
      JOIN eiu.itemUnit iu
      JOIN eiu.eventItem ei
      JOIN ei.event e
      WHERE iu.serial IN :serials
        AND ei.event.id <> :eventId
        AND e.startDate <= :endDate
        AND e.endDate >= :startDate
        AND (ei.status is null or (ei.status <> 'CANCELLED' and ei.status <> 'RETURNED'))
      """)
  List<String> findSerialsInUse(
      @Param("serials") List<String> serials,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("eventId") Long eventId);
}
