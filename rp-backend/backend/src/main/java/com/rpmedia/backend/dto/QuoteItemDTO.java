package com.rpmedia.backend.dto;

import java.math.BigDecimal;

public record QuoteItemDTO(
    Long eventItemId,
    Long itemId,
    String description,
    Integer qty,
    Integer days,
    String rateType,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {}
