package com.rpmedia.backend.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;

@Entity
@Table(name = "items")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Item {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UnitStatus status;

  @Column(nullable = false)
  private String name;

  public Item() {
  }

  public Item(Long id) {
    this.id = id;
  }

  private String description;
  private String imageUrl;

  // JSONB: ใช้ JsonNode + @Type(JsonBinaryType) ตัวเดียว
  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private JsonNode spec;

  @Column(name = "serial_control")
  private Boolean serialControl = false;

  @Column(length = 64, nullable = false)
  private String category; // SOUND/LIGHTING/LED/...

  private String brand;
  private String model;

  @Column(length = 255)
  private String remark;

  @Column(length = 16, nullable = false)
  private String uom; // UNIT / SQM

  @Column(precision = 14, scale = 2)
  private BigDecimal price;

  @Column(name = "rate_type")
  private String rateType;

  private String currency;

  @Column(name = "team_id")
  private Long teamId;

  // Change to Integer to match DB int4
  @Column(name = "total_quantity", nullable = false)
  private Integer totalQuantity;

  @Column(name = "available_quantity")
  private Integer availableQuantity;

  @Transient
  private String serial; // For creation only

  // === getters & setters ===
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public JsonNode getSpec() {
    return spec;
  }

  public void setSpec(JsonNode spec) {
    this.spec = spec;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getBrand() {
    return brand;
  }

  public void setBrand(String brand) {
    this.brand = brand;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getUom() {
    return uom;
  }

  public void setUom(String uom) {
    this.uom = uom;
  }

  public Integer getTotalQuantity() {
    return totalQuantity;
  }

  public void setTotalQuantity(Integer totalQuantity) {
    this.totalQuantity = totalQuantity;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public String getRateType() {
    return rateType;
  }

  public void setRateType(String rateType) {
    this.rateType = rateType;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Long getTeamId() {
    return teamId;
  }

  public void setTeamId(Long teamId) {
    this.teamId = teamId;
  }

  public UnitStatus getStatus() {
    return status;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }

  public void setStatus(UnitStatus status) {
    this.status = status;
  }

  public Integer getAvailableQuantity() {
    return availableQuantity;
  }

  public void setAvailableQuantity(Integer availableQuantity) {
    this.availableQuantity = availableQuantity;
  }

  public String getSerial() {
    return serial;
  }

  public void setSerial(String serial) {
    this.serial = serial;
  }

  public Boolean getSerialControl() {
    return serialControl != null ? serialControl : false;
  }

  public void setSerialControl(Boolean serialControl) {
    this.serialControl = serialControl;
  }

}
