package com.rpmedia.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record QuoteDTO(
    List<QuoteItemDTO> items,
    BigDecimal subtotal
) {}
