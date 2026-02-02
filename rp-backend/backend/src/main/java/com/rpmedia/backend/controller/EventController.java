package com.rpmedia.backend.controller;

import com.rpmedia.backend.model.Event;
import com.rpmedia.backend.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;

import com.rpmedia.backend.dto.CalendarEventDTO;
import com.rpmedia.backend.dto.CreateEventRequest;
import com.rpmedia.backend.dto.CreateEventResponse;
import com.rpmedia.backend.dto.EventDetailDTO;
import com.rpmedia.backend.service.EventService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = { "http://localhost:4200", "http://localhost:3000" }, allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE })
public class EventController {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventService eventService;

    // 🟢 1. Get all events
    @GetMapping
    public ResponseEntity<List<CalendarEventDTO>> getAllEvents() {
        try {
            List<CalendarEventDTO> events = eventService.getAllEventsDTO();
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // 🟢 2. Get single event by ID
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEventById(@PathVariable("id") Long id) {
        return eventRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 🟢 3. Create event (simple)
    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Event> createEvent(@RequestBody Event event) {
        try {
            Event savedEvent = eventRepository.save(event);
            return ResponseEntity.ok(savedEvent);
        } catch (Exception e) {
            return ResponseEntity.status(400).build();
        }
    }

    // 🟢 4. Update event
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Event> updateEvent(@PathVariable("id") Long id, @RequestBody Event updatedEvent) {
        return eventRepository.findById(id)
                .map(existing -> {
                    existing.setTitle(updatedEvent.getTitle());
                    existing.setDescription(updatedEvent.getDescription());
                    existing.setStartDate(updatedEvent.getStartDate());
                    existing.setEndDate(updatedEvent.getEndDate());
                    existing.setStartTime(updatedEvent.getStartTime());
                    existing.setEndTime(updatedEvent.getEndTime());
                    existing.setLocation(updatedEvent.getLocation());
                    existing.setDepartment(updatedEvent.getDepartment());
                    existing.setCreatedBy(updatedEvent.getCreatedBy());
                    if (updatedEvent.getCustomFields() != null)
                        existing.setCustomFields(updatedEvent.getCustomFields());
                    return ResponseEntity.ok(eventRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 🟢 5. Delete event
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Void> deleteEvent(@PathVariable("id") Long id) {
        if (!eventRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        eventRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // 🟢 6. Filter by date range + user
    @GetMapping("/range")
    public List<CalendarEventDTO> listRange(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "userIds", required = false) List<Long> userIds) {
        return eventService.getEvents(from, to, userIds);
    }

    // 🟢 7. Create event from form (DTO)
    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<CreateEventResponse> createFromForm(@RequestBody CreateEventRequest req) {
        var res = eventService.createEvent(req);
        return ResponseEntity.status(201).body(res);
    }

    // 🟢 8. Get event detail (with items)
    @GetMapping("/{id}/detail")
    public ResponseEntity<EventDetailDTO> getEventDetail(@PathVariable("id") Long id) {
        return ResponseEntity.ok(eventService.getEventDetail(id));
    }

    // 🟢 9. Get only Teamup events (แยกเฉพาะ)
    @GetMapping("/teamup")
    public ResponseEntity<List<Event>> getTeamupEvents() {
        List<Event> events = eventRepository.findByExternalSource("TEAMUP");
        return ResponseEntity.ok(events);
    }

    // 🟢 10. (Optional) Get all events including Teamup ones
    // เปลี่ยน path เพื่อไม่ชนกัน
    @GetMapping("/teamup/all")
    public ResponseEntity<List<Event>> getAllTeamupEvents() {
        List<Event> events = eventRepository.findAll();
        return ResponseEntity.ok(events);
    }

}
