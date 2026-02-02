package com.rpmedia.backend.service;

import com.rpmedia.backend.model.Event;
import com.rpmedia.backend.model.EventStatus;
import com.rpmedia.backend.model.ItemStatus;
import com.rpmedia.backend.repository.EventItemRepository;
import com.rpmedia.backend.repository.EventRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class EventLifecycleService {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventItemRepository eventItemRepository;

    @Autowired
    private InventoryService inventoryService;

    @Transactional
    public void updateEventStatuses() {
        LocalDate today = LocalDate.now();

        // 1) CONFIRMED → IN_PROGRESS
        List<Event> starting = eventRepository.findByStatusAndStartDateLessThanEqual(
                EventStatus.CONFIRMED, today);

        for (Event e : starting) {
            e.setStatus(EventStatus.IN_PROGRESS);
            eventRepository.save(e);

            updateItemsStatus(e.getId(), ItemStatus.READY, ItemStatus.IN_USE);
        }

        // 2) IN_PROGRESS → COMPLETED
        List<Event> finished = eventRepository.findByStatusAndEndDateLessThanEqual(
                EventStatus.IN_PROGRESS, today);

        for (Event e : finished) {
            e.setStatus(EventStatus.COMPLETED);
            eventRepository.save(e);

            updateItemsStatus(e.getId(), ItemStatus.IN_USE, ItemStatus.RETURNED);
        }

        // 3) COMPLETED → CANCELLED (หรือ archive)
        List<Event> old = eventRepository.findByStatusAndEndDateBefore(
                EventStatus.COMPLETED, today.minusDays(2));

        for (Event e : old) {
            e.setStatus(EventStatus.CANCELLED);
            eventRepository.save(e);

            updateItemsStatus(e.getId(), ItemStatus.RETURNED, ItemStatus.AVAILABLE);
        }

        System.out.println("EventLifecycleService: Auto-update @ " + today);

        inventoryService.syncVirtualStock(
                LocalDate.now().minusDays(7),
                LocalDate.now().plusDays(30)
        );
    }

    private void updateItemsStatus(Long eventId, ItemStatus from, ItemStatus to) {
        var items = eventItemRepository.findByEventId(eventId);

        items.forEach(ei -> {
            if (ei.getStatus() == from) {
                ei.setStatus(to);
            }
        });

        eventItemRepository.saveAll(items);
    }
}

