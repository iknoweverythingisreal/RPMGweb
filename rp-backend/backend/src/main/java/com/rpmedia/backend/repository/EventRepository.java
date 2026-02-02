package com.rpmedia.backend.repository;

import com.rpmedia.backend.model.Event;
import com.rpmedia.backend.model.EventStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

  // ✅ เพิ่ม method นี้สำหรับ Teamup Integration
  Optional<Event> findByExternalId(String externalId);

  @Query("""
      select e from Event e
      left join fetch e.calendarOwner
      left join fetch e.createdBy
      where e.startDate <= :toDate and e.endDate >= :fromDate
      """)
  List<Event> findInRange(@Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);

  @Query("""
      select e from Event e
      where e.createdBy = :ownerId
        and (
          (e.startDate < :endDate or (e.startDate = :endDate and e.endTime >= :startTime))
          and
          (:startDate < e.endDate or (:startDate = e.endDate and :endTime >= e.startTime))
        )
      """)
  List<Event> findOverlaps(@Param("ownerId") Long ownerId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("startTime") LocalTime startTime,
      @Param("endTime") LocalTime endTime);

  List<Event> findByExternalSource(String externalSource);

  List<Event> findByStatusAndStartDateLessThanEqual(EventStatus status, LocalDate date);

  List<Event> findByStatusAndEndDateLessThanEqual(EventStatus status, LocalDate date);

  List<Event> findByStatusAndEndDateBefore(EventStatus status, LocalDate date);

}
