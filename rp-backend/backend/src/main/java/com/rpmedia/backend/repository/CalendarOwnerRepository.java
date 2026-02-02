package com.rpmedia.backend.repository;

import com.rpmedia.backend.model.CalendarOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CalendarOwnerRepository extends JpaRepository<CalendarOwner, Long> {
    Optional<CalendarOwner> findByTeamupSubcalendarId(Long teamupSubcalendarId);
}
