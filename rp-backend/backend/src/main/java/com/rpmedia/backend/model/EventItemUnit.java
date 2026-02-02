package com.rpmedia.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_item_units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventItemUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK → event_items.id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_item_id", nullable = false)
    private EventItem eventItem;

    // FK → item_units.id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_unit_id")
    private ItemUnit itemUnit;

    @Column(length = 20)
    private String status;  // default: PICKED

    private LocalDateTime pickedAt;

    private LocalDateTime outAt;

    private LocalDateTime returnedAt;

    @Column(columnDefinition = "text")
    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
