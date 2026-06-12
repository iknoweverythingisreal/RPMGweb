package com.rpmedia.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class SerialActionRequest {

    private Long eventItemId;
    private List<Long> unitIds;
    private Long userId;
    private String note;
}
