package com.rpmedia.backend.service;

import com.rpmedia.backend.dto.CalendarEventDTO;
import com.rpmedia.backend.dto.CreateEventRequest;
import com.rpmedia.backend.dto.CreateEventResponse;
import com.rpmedia.backend.model.Event;
import com.rpmedia.backend.model.User;
import com.rpmedia.backend.repository.EventRepository;
import com.rpmedia.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.rpmedia.backend.dto.EventDetailDTO;
import com.rpmedia.backend.dto.EventItemDTO;
import com.rpmedia.backend.model.CalendarOwner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EventHistoryService eventHistoryService;

    // ========= เมธอดเดิม (คงไว้) =========
    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    public Optional<Event> getEventById(Long id) {
        return eventRepository.findById(id);
    }

    public Event createEvent(Event event) {
        return eventRepository.save(event);
    }

    public Event updateEvent(Long id, Event updatedEvent) {
        return eventRepository.findById(id).map(event -> {
            event.setTitle(updatedEvent.getTitle());
            event.setDescription(updatedEvent.getDescription());
            event.setStartDate(updatedEvent.getStartDate());
            event.setEndDate(updatedEvent.getEndDate());
            event.setStartTime(updatedEvent.getStartTime());
            event.setEndTime(updatedEvent.getEndTime());
            event.setLocation(updatedEvent.getLocation());
            event.setDepartment(updatedEvent.getDepartment());
            event.setCreatedBy(updatedEvent.getCreatedBy()); // relation อยู่แล้ว OK
            return eventRepository.save(event);
        }).orElseThrow(() -> new RuntimeException("Event not found"));
    }

    public void deleteEvent(Long id) {
        eventRepository.deleteById(id);
    }

    // ========= เมธอดใหม่สำหรับ Calendar =========
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<CalendarEventDTO> getAllEventsDTO() {
        return eventRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** ดึง events ตามช่วงวัน และกรองตาม userIds (เจ้าของปฏิทิน) */
    public List<CalendarEventDTO> getEvents(LocalDate fromDate, LocalDate toDate, List<Long> userIds) {
        List<Event> events = eventRepository.findInRange(fromDate, toDate);

        // ✅ กรองตาม userIds (ถ้ามี)
        if (userIds != null && !userIds.isEmpty()) {
            events = events.stream()
                    .filter(e -> e.getCreatedBy() != null && userIds.contains(e.getCreatedBy().getId()))
                    .collect(Collectors.toList());
        }

        return events.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** สร้าง event จาก payload แบบฟอร์ม และคืน warnings ถ้าทับเวลา */
    public CreateEventResponse createEvent(CreateEventRequest r) {
        // หา overlap เฉพาะ owner เดียวกัน (EventRepository ต้องใช้ e.createdBy.id =
        // :ownerId)
        var overlaps = eventRepository.findOverlaps(
                r.getOwnerId(), r.getStartDate(), r.getEndDate(), r.getStartTime(), r.getEndTime());

        // ถ้าคนละ location ไม่เตือน; ถ้า location เดียวกัน ให้เตือน
        boolean sameLocationOverlap = overlaps.stream().anyMatch(ev -> {
            String a = ev.getLocation() == null ? "" : ev.getLocation();
            String b = r.getLocation() == null ? "" : r.getLocation();
            return a.equalsIgnoreCase(b);
        });

        List<String> warnings = new ArrayList<>();
        if (sameLocationOverlap) {
            warnings.add("This event overlaps other events for the same owner at the same location.");
        }

        // map -> entity
        Event e = new Event();
        e.setTitle(r.getTitle());
        e.setDescription(r.getDescription());
        e.setLocation(r.getLocation());
        e.setStartDate(r.getStartDate());
        e.setEndDate(r.getEndDate());
        e.setStartTime(r.getStartTime());
        e.setEndTime(r.getEndTime());

        // ✅ createdBy เป็น relation → ต้องดึง User มาก่อน
        User owner = userRepository.findById(r.getOwnerId())
                .orElseThrow(() -> new RuntimeException("Owner not found: " + r.getOwnerId()));
        e.setCreatedBy(owner);

        Map<String, Object> cf = r.getCustomFields() != null
                ? new HashMap<>(r.getCustomFields())
                : new HashMap<>();
        if (r.getType() != null)
            cf.put("type", r.getType());
        e.setCustomFields(cf);

        var saved = eventRepository.save(e);

        // 🔹 Log EVENT_CREATED to history
        eventHistoryService.log(saved.getId(), r.getOwnerId(), "EVENT_CREATED",
                "Event created: " + saved.getTitle());

        return CreateEventResponse.builder()
                .id(saved.getId())
                .warnings(warnings)
                .build();
    }

    // ========= helper =========
    private CalendarEventDTO toDto(Event e) {
        String ownerName = null;
        String ownerColor = "#888";
        String ownerRole = null;
        Long ownerId = null;

        // Priority 1: CalendarOwner (Teamup)
        if (e.getCalendarOwner() != null) {
            CalendarOwner co = e.getCalendarOwner();
            ownerId = co.getId();
            ownerName = co.getName();
            if (co.getColorHex() != null && !co.getColorHex().isBlank()) {
                ownerColor = co.getColorHex();
            }
        }
        // Priority 2: CreatedBy (Internal User)
        else if (e.getCreatedBy() != null) {
            User owner = e.getCreatedBy();
            ownerId = owner.getId();
            ownerName = (owner.getName() != null && !owner.getName().isBlank())
                    ? owner.getName()
                    : owner.getEmail();
            ownerRole = owner.getRole() != null ? owner.getRole().name() : null; // Extract role for frontend
            if (owner.getCalendarColor() != null && !owner.getCalendarColor().isBlank()) {
                ownerColor = owner.getCalendarColor();
            }
        }

        // Collect all colors for gradient (owner + managers).
        // Always include one color per participant (fallback gray when unset),
        // deduplicated so the same person/color doesn't create fake stripes.
        java.util.List<String> allColors = new java.util.ArrayList<>();
        allColors.add(ownerColor); // ownerColor already falls back to #888

        if (e.getManagers() != null && !e.getManagers().isEmpty()) {
            for (User manager : e.getManagers()) {
                // Skip the creator if they assigned themselves as manager
                if (e.getCreatedBy() != null && manager.getId().equals(e.getCreatedBy().getId())) {
                    continue;
                }
                String managerColor = (manager.getCalendarColor() != null && !manager.getCalendarColor().isBlank())
                        ? manager.getCalendarColor()
                        : "#64748b";
                if (!allColors.contains(managerColor)) {
                    allColors.add(managerColor);
                }
            }
        }

        String type = null;
        if (e.getCustomFields() != null && e.getCustomFields().get("type") != null) {
            type = String.valueOf(e.getCustomFields().get("type"));
        }

        CalendarEventDTO dto = CalendarEventDTO.builder()
                .id(e.getId())
                .title(e.getTitle())
                .description(e.getDescription())
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .startTime(e.getStartTime())
                .endTime(e.getEndTime())
                .ownerId(ownerId)
                .ownerName(ownerName)
                .ownerColorHex(ownerColor)
                .ownerRole(ownerRole)
                .allColors(allColors.isEmpty() ? null : allColors)
                .type(type)
                .location(e.getLocation())
                .teamupSubcalendarIds(e.getTeamupSubcalendarIds())
                .managerIds(
                        e.getManagers() != null ? e.getManagers().stream().map(User::getId).collect(Collectors.toList())
                                : null)
                .techLeadId(e.getTechLead() != null ? e.getTechLead().getId() : null)
                .build();

        if (dto.getId() == 827 || (dto.getAllColors() != null && !dto.getAllColors().isEmpty())) {
            System.out.println("[DEBUG] DTO " + dto.getId() + " colors: " + dto.getOwnerColorHex() + " | all: "
                    + dto.getAllColors());
        }

        return dto;
    }

    public EventDetailDTO getEventDetail(Long id) {
        Event e = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        List<EventItemDTO> items = e.getEventItems().stream()
                .map(i -> EventItemDTO.builder()
                        .id(i.getId())
                        .itemId(i.getItem().getId())
                        .itemName(i.getItem().getName())
                        .category(i.getItem().getCategory())
                        .uom(i.getItem().getUom())
                        .requestedQuantity(
                                i.getRequestedQuantity() != null ? BigDecimal.valueOf(i.getRequestedQuantity()) : null)
                        .allocatedQuantity(
                                i.getAllocatedQuantity() != null ? BigDecimal.valueOf(i.getAllocatedQuantity()) : null)
                        .unitPrice(i.getUnitPrice())
                        .rateType(i.getRateType())
                        .lineTotal(i.getLineTotal())
                        .status(i.getStatus() != null ? i.getStatus().name() : null)
                        .remark(i.getRemark())
                        .room(i.getMetadata() != null && i.getMetadata().has("room")
                                ? i.getMetadata().get("room").asText()
                                : null)
                        .build())
                .toList();

        return EventDetailDTO.builder()
                .id(e.getId())
                .title(e.getTitle())
                .description(e.getDescription())
                .location(e.getLocation())
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .startTime(e.getStartTime())
                .endTime(e.getEndTime())
                .items(items)
                .build();
    }

}
