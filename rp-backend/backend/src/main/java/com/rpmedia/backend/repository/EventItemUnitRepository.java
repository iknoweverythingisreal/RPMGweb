package com.rpmedia.backend.repository;

import com.rpmedia.backend.model.EventItemUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EventItemUnitRepository extends JpaRepository<EventItemUnit, Long> {

    // หา units ทั้งหมดของ EventItem
    List<EventItemUnit> findByEventItemId(Long eventItemId);

    // หา EventItemUnit จาก serial (ผ่าน itemUnit.serial)
    List<EventItemUnit> findByItemUnit_Serial(String serial);

    // หา units จาก eventId
    List<EventItemUnit> findByEventItem_Event_Id(Long eventId);

    /**
     * ดึง serials ที่ถูกใช้อยู่ (ซ้อนทับช่วงเวลา)
     * - join itemUnit เพื่อดู serial
     * - match itemId
     * - match event range
     */
    @Query("""
            SELECT iu.serial
            FROM EventItemUnit eiu
            JOIN eiu.itemUnit iu
            JOIN eiu.eventItem ei
            JOIN ei.event e
            WHERE ei.item.id = :itemId
              AND e.startDate <= :endDate
              AND e.endDate >= :startDate
            """)
    List<String> findSerialsInUseForItem(
            @Param("itemId") Long itemId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
