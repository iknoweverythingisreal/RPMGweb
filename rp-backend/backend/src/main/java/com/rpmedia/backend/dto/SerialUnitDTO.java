package com.rpmedia.backend.dto;

import java.time.LocalDateTime;

public class SerialUnitDTO {
    private Long itemUnitId;
    private String serial;
    private String status;
    private LocalDateTime pickedAt;
    private LocalDateTime outAt;
    private LocalDateTime returnedAt;
    private String note;

    public SerialUnitDTO(Long itemUnitId, String serial, String status,
                         LocalDateTime pickedAt, LocalDateTime outAt,
                         LocalDateTime returnedAt, String note) {
        this.itemUnitId = itemUnitId;
        this.serial = serial;
        this.status = status;
        this.pickedAt = pickedAt;
        this.outAt = outAt;
        this.returnedAt = returnedAt;
        this.note = note;
    }

    public Long getItemUnitId() { return itemUnitId; }
    public String getSerial() { return serial; }
    public String getStatus() { return status; }
    public LocalDateTime getPickedAt() { return pickedAt; }
    public LocalDateTime getOutAt() { return outAt; }
    public LocalDateTime getReturnedAt() { return returnedAt; }
    public String getNote() { return note; }

    public void setItemUnitId(Long id) { this.itemUnitId = id; }
    public void setSerial(String s) { this.serial = s; }
    public void setStatus(String s) { this.status = s; }
    public void setPickedAt(LocalDateTime t) { this.pickedAt = t; }
    public void setOutAt(LocalDateTime t) { this.outAt = t; }
    public void setReturnedAt(LocalDateTime t) { this.returnedAt = t; }
    public void setNote(String n) { this.note = n; }
}
