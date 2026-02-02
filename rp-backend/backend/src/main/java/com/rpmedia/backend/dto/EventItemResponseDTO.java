package com.rpmedia.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class EventItemResponseDTO {
    private Long eventItemId;
    private Long itemId;
    private String itemName;
    private BigDecimal requestedQuantity;
    private BigDecimal allocatedQuantity;
    private BigDecimal overbookQty;
    private String overbookStatus;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private String status;
    private String remark;
}
