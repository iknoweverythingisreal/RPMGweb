package com.rpmedia.backend.dto;

import lombok.Data;

@Data
public class SwapItemRequestDTO {
    private Long sourceEventItemId; // The item being removed (from source event)
    private Long targetItemId; // The replacement item (from storage or target event)
    private Long targetEventId; // Optional: If swapping with another event, this is that event's ID. If null,
                                // treated as Storage swap.
    private Long targetEventItemId; // Optional: If swapping with another event, this is the existing EventItem
                                    // record ID in that event.

    // "Move to Store" specific
    private boolean moveToStore; // If true, source item goes to storage. targetItemId is required as the
                                 // replacement for source event.

    private String swapMode; // MUTUAL or ONE_WAY
    private String reason; // Why is this swap happening?
    private String targetType; // STORAGE or EVENT
    private Integer quantity; // How many units to swap
    private Long userId; // User performing the action
}
