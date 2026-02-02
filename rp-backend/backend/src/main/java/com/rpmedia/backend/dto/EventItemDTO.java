package com.rpmedia.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class EventItemDTO {
    private Long id; // event_items.id
    private Long eventId; // event.id
    private String eventName; // event.name
    private Long itemId; // items.id
    private String itemName; // items.name
    private String category; // items.category
    private String brand; // items.brand
    private String model; // items.model
    private String uom; // items.uom (UNIT, SQM)

    private BigDecimal requestedQuantity; // event_items.requested_quantity
    private BigDecimal allocatedQuantity; // event_items.allocated_quantity

    private BigDecimal unitPrice; // event_items.unit_price
    private String rateType; // event_items.rate_type
    private BigDecimal lineTotal; // event_items.line_total

    private String status; // event_items.status (เช่น ALLOCATED/SHORTAGE)
    private String remark; // event_items.remark

    // ========= NEW: Overbooking fields =========
    private BigDecimal overbookQty; // จำนวนที่เกิน
    private String overbookStatus; // NONE / PENDING / APPROVED / REJECTED
    private String overbookNote; // หมายเหตุจากการอนุมัติ
    private Long overbookApprovedBy; // userId ของ CEO ที่อนุมัติ
    private LocalDateTime overbookApprovedAt; // เวลาที่อนุมัติ
    private List<AssignedSerialDTO> serials;
}
