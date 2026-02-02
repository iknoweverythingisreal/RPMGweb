package com.rpmedia.backend.repository;

import com.rpmedia.backend.model.EventHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventHistoryRepository extends JpaRepository<EventHistory, Long> {
    List<EventHistory> findByEventId(Long eventId);

    List<EventHistory> findByEventIdOrderByChangedAtDesc(Long eventId);

}
