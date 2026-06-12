package com.rpmedia.backend.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_items")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class EventItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "event_id", nullable = false)
  private Event event;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "item_id", nullable = false)
  private Item item;

  @Column(name = "return_date")
  private LocalDate returnDate;

  @Column(name = "quantity")
  private Integer quantity;

  @Column(name = "requested_quantity")
  private Integer requestedQuantity;

  @Column(name = "allocated_quantity")
  private Integer allocatedQuantity;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private ItemStatus status = ItemStatus.REQUESTED;

  @Column(name = "unit_price")
  private BigDecimal unitPrice;

  @Column(name = "rate_type")
  private String rateType;

  @Column(name = "line_total")
  private BigDecimal lineTotal;

  private String remark;

  @Column(name = "allocated_at")
  private LocalDateTime allocatedAt;

  @Column(name = "prepared_at")
  private LocalDateTime preparedAt;

  @Column(name = "checked_at")
  private LocalDateTime checkedAt;

  @Column(name = "returned_at")
  private LocalDateTime returnedAt;

  @Column(name = "picked_qty")
  private Integer pickedQty;

  @Column(name = "out_qty")
  private Integer outQty;

  @Column(name = "returned_quantity")
  private Integer returnedQuantity;

  @Column(name = "damaged_qty")
  private Integer damagedQty;

  @Column(name = "lost_qty")
  private Integer lostQty;

  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private JsonNode serials;

  private Integer overbookQty = 0;

  @Enumerated(EnumType.STRING)
  @Column(length = 16, nullable = false)
  private OverbookStatus overbookStatus = OverbookStatus.NONE;

  @Column(name = "team_id")
  private Long teamId;

  private String source;

  @Column(name = "prepared_by")
  private Long preparedBy;

  @Column(name = "checked_by")
  private Long checkedBy;

  @Column(name = "confirmed_by")
  private Long confirmedBy;

  private String overbookNote;
  private Long overbookApprovedBy;
  private LocalDateTime overbookApprovedAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at")
  private LocalDateTime updatedAt = LocalDateTime.now();

  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private JsonNode metadata;

  @Version
  private Long version = 0L;

  // === Getters & Setters ===

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Event getEvent() {
    return event;
  }

  public void setEvent(Event event) {
    this.event = event;
  }

  public Item getItem() {
    return item;
  }

  public void setItem(Item item) {
    this.item = item;
  }

  public LocalDate getReturnDate() {
    return returnDate;
  }

  public void setReturnDate(LocalDate returnDate) {
    this.returnDate = returnDate;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }

  public Integer getRequestedQuantity() {
    return requestedQuantity;
  }

  public void setRequestedQuantity(Integer requestedQuantity) {
    this.requestedQuantity = requestedQuantity;
  }

  public Integer getAllocatedQuantity() {
    return allocatedQuantity;
  }

  public void setAllocatedQuantity(Integer allocatedQuantity) {
    this.allocatedQuantity = allocatedQuantity;
  }

  public ItemStatus getStatus() {
    return status;
  }

  public void setStatus(ItemStatus status) {
    this.status = status;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public void setUnitPrice(BigDecimal unitPrice) {
    this.unitPrice = unitPrice;
  }

  public String getRateType() {
    return rateType;
  }

  public void setRateType(String rateType) {
    this.rateType = rateType;
  }

  public BigDecimal getLineTotal() {
    return lineTotal;
  }

  public void setLineTotal(BigDecimal lineTotal) {
    this.lineTotal = lineTotal;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }

  public LocalDateTime getAllocatedAt() {
    return allocatedAt;
  }

  public void setAllocatedAt(LocalDateTime allocatedAt) {
    this.allocatedAt = allocatedAt;
  }

  public LocalDateTime getPreparedAt() {
    return preparedAt;
  }

  public void setPreparedAt(LocalDateTime preparedAt) {
    this.preparedAt = preparedAt;
  }

  public LocalDateTime getCheckedAt() {
    return checkedAt;
  }

  public void setCheckedAt(LocalDateTime checkedAt) {
    this.checkedAt = checkedAt;
  }

  public LocalDateTime getReturnedAt() {
    return returnedAt;
  }

  public void setReturnedAt(LocalDateTime returnedAt) {
    this.returnedAt = returnedAt;
  }

  public Integer getPickedQty() {
    return pickedQty;
  }

  public void setPickedQty(Integer pickedQty) {
    this.pickedQty = pickedQty;
  }

  public Integer getOutQty() {
    return outQty;
  }

  public void setOutQty(Integer outQty) {
    this.outQty = outQty;
  }

  public Integer getReturnedQuantity() {
    return returnedQuantity;
  }

  public void setReturnedQuantity(Integer returnedQuantity) {
    this.returnedQuantity = returnedQuantity;
  }

  public Integer getDamagedQty() {
    return damagedQty;
  }

  public void setDamagedQty(Integer damagedQty) {
    this.damagedQty = damagedQty;
  }

  public Integer getLostQty() {
    return lostQty;
  }

  public void setLostQty(Integer lostQty) {
    this.lostQty = lostQty;
  }

  public JsonNode getSerials() {
    return serials;
  }

  public void setSerials(JsonNode serials) {
    this.serials = serials;
  }

  public Integer getOverbookQty() {
    return overbookQty;
  }

  public void setOverbookQty(Integer overbookQty) {
    this.overbookQty = overbookQty;
  }

  public OverbookStatus getOverbookStatus() {
    return overbookStatus;
  }

  public void setOverbookStatus(OverbookStatus overbookStatus) {
    this.overbookStatus = overbookStatus;
  }

  public Long getTeamId() {
    return teamId;
  }

  public void setTeamId(Long teamId) {
    this.teamId = teamId;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public Long getPreparedBy() {
    return preparedBy;
  }

  public void setPreparedBy(Long preparedBy) {
    this.preparedBy = preparedBy;
  }

  public Long getCheckedBy() {
    return checkedBy;
  }

  public void setCheckedBy(Long checkedBy) {
    this.checkedBy = checkedBy;
  }

  public Long getConfirmedBy() {
    return confirmedBy;
  }

  public void setConfirmedBy(Long confirmedBy) {
    this.confirmedBy = confirmedBy;
  }

  public String getOverbookNote() {
    return overbookNote;
  }

  public void setOverbookNote(String overbookNote) {
    this.overbookNote = overbookNote;
  }

  public Long getOverbookApprovedBy() {
    return overbookApprovedBy;
  }

  public void setOverbookApprovedBy(Long overbookApprovedBy) {
    this.overbookApprovedBy = overbookApprovedBy;
  }

  public LocalDateTime getOverbookApprovedAt() {
    return overbookApprovedAt;
  }

  public void setOverbookApprovedAt(LocalDateTime overbookApprovedAt) {
    this.overbookApprovedAt = overbookApprovedAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public JsonNode getMetadata() {
    return metadata;
  }

  public void setMetadata(JsonNode metadata) {
    this.metadata = metadata;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }
}
