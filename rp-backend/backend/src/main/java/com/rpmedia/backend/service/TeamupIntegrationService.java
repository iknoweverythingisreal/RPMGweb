package com.rpmedia.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpmedia.backend.model.CalendarOwner;
import com.rpmedia.backend.model.Event;
import com.rpmedia.backend.repository.CalendarOwnerRepository;
import com.rpmedia.backend.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TeamupIntegrationService {

    private final EventRepository eventRepository;
    private final CalendarOwnerRepository calendarOwnerRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${teamup.api.key:TEAMUP_API_KEY_PLACEHOLDER}")
    private String teamupApiKey;

    @Value("${teamup.calendar.key:TEAMUP_CALENDAR_KEY_PLACEHOLDER}")
    private String calendarKey;

    private static final String TEAMUP_API_BASE = "https://api.teamup.com/";

    /** 🔹 ดึง Event ล่าสุดจาก Teamup (default = แค่ช่วงสั้นๆ) */
    public int syncFromTeamup() {
        String url = TEAMUP_API_BASE + calendarKey + "/events";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Teamup-Token", teamupApiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Teamup API error: " + response.getStatusCode());
        }

        return parseAndSaveEvents(response.getBody());
    }

    /** 🔹 ดึง Event ตามช่วงเวลา → ใช้ startDate / endDate */
    public int syncFromTeamupRange(LocalDate from, LocalDate to) {
        String url = UriComponentsBuilder
                .fromHttpUrl(TEAMUP_API_BASE + calendarKey + "/events")
                .queryParam("startDate", from)
                .queryParam("endDate", to)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Teamup-Token", teamupApiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Teamup API error: " + response.getStatusCode());
        }

        return parseAndSaveEvents(response.getBody());
    }

    /** 🔹 ฟังก์ชันย่อยสำหรับ parse JSON และบันทึกลง DB */
    @Transactional
    protected int parseAndSaveEvents(String jsonBody) {
        int imported = 0;
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode events = root.get("events");
            if (events != null && events.isArray()) {
                for (JsonNode node : events) {
                    String externalId = node.get("id").asText();
                    Optional<Event> existing = eventRepository.findByExternalId(externalId);
                    Event e = existing.orElse(new Event());

                    e.setExternalSource("TEAMUP");
                    e.setExternalId(externalId);
                    e.setTitle(node.path("title").asText(""));
                    e.setDescription(node.path("notes").asText(""));

                    // --- Translating date/time ---
                    String startDt = node.path("start_dt").asText("");
                    String endDt = node.path("end_dt").asText("");
                    if (!startDt.isEmpty() && !endDt.isEmpty()) {
                        e.setStartDate(LocalDate.parse(startDt.substring(0, 10)));
                        e.setEndDate(LocalDate.parse(endDt.substring(0, 10)));
                    }

                    // --- Strip HTML from Notes/Description ---
                    String rawNotes = node.path("notes").asText("");
                    if (rawNotes != null) {
                        // Strip tags, replace &nbsp; with space, and trim
                        String cleanNotes = rawNotes
                                .replaceAll("<[^>]*>", "")
                                .replaceAll("&nbsp;", " ")
                                .trim();
                        e.setDescription(cleanNotes);
                    }

                    // --- จัดการ Owner (Sub-calendars) ---
                    JsonNode subIdsNode = node.get("subcalendar_ids");
                    if (subIdsNode != null && subIdsNode.isArray()) {
                        List<Long> subIds = new ArrayList<>();
                        for (JsonNode subIdNode : subIdsNode) {
                            subIds.add(subIdNode.asLong());
                        }
                        e.setTeamupSubcalendarIds(subIds);

                        if (!subIds.isEmpty()) {
                            Long primarySubId = subIds.get(0);
                            calendarOwnerRepository.findByTeamupSubcalendarId(primarySubId)
                                    .ifPresent(e::setCalendarOwner);
                        }
                    }

                    e.setUpdatedAt(LocalDateTime.now());
                    eventRepository.save(e);
                    imported++;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error parsing Teamup API response", e);
        }
        return imported;
    }

    /** 🔹 Sync Sub-calendars (Owners) จาก Teamup */
    @Transactional
    public int syncOwnersFromTeamup() {
        String url = TEAMUP_API_BASE + calendarKey + "/subcalendars";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Teamup-Token", teamupApiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Teamup API error: " + response.getStatusCode());
        }

        int synced = 0;
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode subcalendars = root.get("subcalendars");
            if (subcalendars != null && subcalendars.isArray()) {
                for (JsonNode node : subcalendars) {
                    Long subId = node.get("id").asLong();
                    CalendarOwner owner = calendarOwnerRepository.findByTeamupSubcalendarId(subId)
                            .orElse(new CalendarOwner());

                    owner.setTeamupSubcalendarId(subId);
                    owner.setName(node.path("name").asText("Unknown"));
                    owner.setIsActive(node.path("active").asBoolean(true));

                    // Map Teamup color index to Hex
                    // PROTECTION: Only update color if it's currently null or empty to preserve
                    // manual database fixes
                    String currentColor = owner.getColorHex();
                    if (currentColor == null || currentColor.trim().isEmpty()
                            || currentColor.equalsIgnoreCase("#3a87ad")) {
                        int colorIndex = node.path("color").asInt(0);
                        owner.setColorHex(com.rpmedia.backend.util.TeamupColorUtil.getHexColor(colorIndex));
                    }

                    calendarOwnerRepository.save(owner);
                    synced++;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error parsing Teamup subcalendars", e);
        }
        return synced;
    }

    /** 🔹 ส่ง Event ที่สร้างในระบบเรา → Teamup */
    public void pushEventToTeamup(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found with ID: " + eventId));

        String url = TEAMUP_API_BASE + calendarKey + "/events";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Teamup-Token", teamupApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            JsonNode eventJson = objectMapper.createObjectNode()
                    .put("title", event.getTitle())
                    .put("start_dt", event.getStartDate() + "T" + event.getStartTime())
                    .put("end_dt", event.getEndDate() + "T" + event.getEndTime())
                    .put("notes", event.getDescription());

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(eventJson), headers);
            restTemplate.postForEntity(url, request, String.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to push event to Teamup", e);
        }
    }

    public int syncChangesSince(LocalDateTime lastSyncTime) {
        String url = UriComponentsBuilder
                .fromHttpUrl(TEAMUP_API_BASE + calendarKey + "/events")
                .queryParam("modifiedSince", lastSyncTime.toString())
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Teamup-Token", teamupApiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Teamup API error: " + response.getStatusCode());
        }

        return parseAndSaveEvents(response.getBody());
    }

    public int backfillAllYears() {
        int total = 0;
        LocalDate to = LocalDate.now().plusMonths(1);
        LocalDate from = to.minusYears(3); // ย้อนหลัง 3 ปี

        while (from.isBefore(to)) {
            LocalDate rangeTo = from.plusMonths(2);
            System.out.println("Fetching from " + from + " to " + rangeTo);
            total += syncFromTeamupRange(from, rangeTo);
            from = rangeTo.plusDays(1);
        }

        return total;
    }

}
