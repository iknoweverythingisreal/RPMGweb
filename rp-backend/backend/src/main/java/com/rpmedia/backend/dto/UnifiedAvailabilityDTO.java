package com.rpmedia.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class UnifiedAvailabilityDTO {

    private Long itemId;
    private boolean serialMode;

    // For serial-mode
    private List<SerialAvailabilityDTO> serials;

    // For qty-mode
    private ItemQuantityAvailabilityDTO qty;

    // Common fields
    private String uom;
    private String itemName;
    private int total;
    private int allocated;
    private int available;
    private int shortage;

}
