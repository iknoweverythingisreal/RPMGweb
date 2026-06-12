package com.rpmedia.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AddEventItemRequest(
    Long itemId,
    Integer requestedQuantity,
    LocalDate returnDate,
    BigDecimal unitPrice,      
    String rateType              
) {}
