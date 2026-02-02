package com.rpmedia.backend.dto;

import java.math.BigDecimal;

public interface EquipmentAvailabilityRow {
    Long getId();

    String getName();

    String getCategory();

    String getBrand();

    String getModel();

    String getUom();

    Integer getTotalQuantity();

    Integer getAvailable();
}
