package com.rpmedia.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class LinkUnitsRequest {
    private List<Long> unitIds;
    private String note;
}
