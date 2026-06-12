package com.rpmedia.backend.dto;

public class ItemShortageDTO {
    private Long itemId;
    private String itemName;
    private int totalQuantity;
    private int requestedQuantity;
    private int bookedQuantity;
    private int availableQuantity;
    private int shortageAmount;

    public ItemShortageDTO(Long itemId, String itemName, int totalQuantity, int requestedQuantity,
                           int bookedQuantity, int availableQuantity, int shortageAmount) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.totalQuantity = totalQuantity;
        this.requestedQuantity = requestedQuantity;
        this.bookedQuantity = bookedQuantity;
        this.availableQuantity = availableQuantity;
        this.shortageAmount = shortageAmount;
    }

    // ✅ ใส่ getter/setter ให้ครบ

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public void setRequestedQuantity(int requestedQuantity) {
        this.requestedQuantity = requestedQuantity;
    }

    public int getBookedQuantity() {
        return bookedQuantity;
    }

    public void setBookedQuantity(int bookedQuantity) {
        this.bookedQuantity = bookedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public int getShortageAmount() {
        return shortageAmount;
    }

    public void setShortageAmount(int shortageAmount) {
        this.shortageAmount = shortageAmount;
    }
}
