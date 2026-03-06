package com.rpmedia.backend.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventItemRequestDTO {
    private Long eventId;
    private Long itemId;
    private Integer requestedQuantity;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal unitPrice;
    private String rateType; // PER_DAY / PER_EVENT
    private String remark;
    private List<String> serials;
    private Integer qty;
    private String overbookNote;
    private String status;
    private String source;
    private Boolean autoApprove;
}
