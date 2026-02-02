package com.rpmedia.backend.dto;

import lombok.Data;

@Data
public class ItemQuantityAvailabilityDTO {
    private int total;      // total stock
    private int allocated;  // allocated in other events overlapping
    private int available;  // remaining
    private int shortage;   // positive number if not enough
}
