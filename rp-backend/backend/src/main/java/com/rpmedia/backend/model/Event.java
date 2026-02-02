package com.rpmedia.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
@Table(name = "events")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;

    private String location;

    @Column(name = "external_source")
    private String externalSource;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    // Managers of this event (multiple allowed)
    @ManyToMany
    @JoinTable(name = "event_managers", joinColumns = @JoinColumn(name = "event_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private List<User> managers = new ArrayList<>();
    private Boolean locked = false;

    // Tech lead for this event
    @ManyToOne
    @JoinColumn(name = "tech_lead_id")
    private User techLead;

    @Type(value = JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> customFields = new HashMap<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_owner_id")
    private CalendarOwner calendarOwner;

    @Type(value = JsonBinaryType.class)
    @Column(name = "teamup_subcalendar_ids", columnDefinition = "jsonb")
    private List<Long> teamupSubcalendarIds = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EventItem> eventItems = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private EventStatus status = EventStatus.DRAFT;

    // === Workflow timestamps ===
    private LocalDateTime confirmedAt;
    private LocalDateTime preparedAt;
    private LocalDateTime checkedAt;
    private LocalDateTime completedAt;
    // === Overbook workflow ===
    private Boolean overbookRequested = false;
    private Boolean overbookApproved = false;
    private Long overbookApprovedBy;
    private LocalDateTime overbookApprovedAt;
    private String overbookNote;
    // === Rent workflow ===
    private Boolean rentRequested = false;
    private Boolean rentApproved = false;
    private Long rentApprovedBy;
    private LocalDateTime rentApprovedAt;
    private String rentNote;

    @Column(name = "version")
    private Integer version = 1;

    // === GETTERS & SETTERS ===
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public Map<String, Object> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields;
    }

    public List<EventItem> getEventItems() {
        return eventItems;
    }

    public void setEventItems(List<EventItem> eventItems) {
        this.eventItems = eventItems;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Event() {
    }

    public Event(Long id) {
        this.id = id;

    }

    public String getExternalSource() {
        return externalSource;
    }

    public void setExternalSource(String externalSource) {
        this.externalSource = externalSource;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public LocalDateTime getPreparedAt() {
        return preparedAt;
    }

    public void setPreparedAt(LocalDateTime preparedAt) {
        this.preparedAt = preparedAt;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Boolean getOverbookRequested() {
        return overbookRequested;
    }

    public void setOverbookRequested(Boolean overbookRequested) {
        this.overbookRequested = overbookRequested;
    }

    public Boolean getOverbookApproved() {
        return overbookApproved;
    }

    public void setOverbookApproved(Boolean overbookApproved) {
        this.overbookApproved = overbookApproved;
    }

    public Long getOverbookApprovedBy() {
        return overbookApprovedBy;
    }

    public void setOverbookApprovedBy(Long overbookApprovedBy) {
        this.overbookApprovedBy = overbookApprovedBy;
    }

    public LocalDateTime getOverbookApprovedAt() {
        return overbookApprovedAt;
    }

    public void setOverbookApprovedAt(LocalDateTime overbookApprovedAt) {
        this.overbookApprovedAt = overbookApprovedAt;
    }

    public String getOverbookNote() {
        return overbookNote;
    }

    public void setOverbookNote(String overbookNote) {
        this.overbookNote = overbookNote;
    }

    public Boolean getRentRequested() {
        return rentRequested;
    }

    public void setRentRequested(Boolean rentRequested) {
        this.rentRequested = rentRequested;
    }

    public Boolean getRentApproved() {
        return rentApproved;
    }

    public void setRentApproved(Boolean rentApproved) {
        this.rentApproved = rentApproved;
    }

    public Long getRentApprovedBy() {
        return rentApprovedBy;
    }

    public void setRentApprovedBy(Long rentApprovedBy) {
        this.rentApprovedBy = rentApprovedBy;
    }

    public LocalDateTime getRentApprovedAt() {
        return rentApprovedAt;
    }

    public void setRentApprovedAt(LocalDateTime rentApprovedAt) {
        this.rentApprovedAt = rentApprovedAt;
    }

    public String getRentNote() {
        return rentNote;
    }

    public void setRentNote(String rentNote) {
        this.rentNote = rentNote;
    }

    public List<User> getManagers() {
        return managers;
    }

    public void setManagers(List<User> managers) {
        this.managers = managers;
    }

    public User getTechLead() {
        return techLead;
    }

    public void setTechLead(User techLead) {
        this.techLead = techLead;
    }

    public Boolean getLocked() {
        return locked;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public CalendarOwner getCalendarOwner() {
        return calendarOwner;
    }

    public void setCalendarOwner(CalendarOwner calendarOwner) {
        this.calendarOwner = calendarOwner;
    }

    public List<Long> getTeamupSubcalendarIds() {
        return teamupSubcalendarIds;
    }

    public void setTeamupSubcalendarIds(List<Long> teamupSubcalendarIds) {
        this.teamupSubcalendarIds = teamupSubcalendarIds;
    }

}
