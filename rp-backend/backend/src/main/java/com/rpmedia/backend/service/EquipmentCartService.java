package com.rpmedia.backend.service;

import com.rpmedia.backend.dto.AddEventItemRequest;
import com.rpmedia.backend.dto.QuoteDTO;
import com.rpmedia.backend.dto.QuoteItemDTO;
import com.rpmedia.backend.model.Event;
import com.rpmedia.backend.model.EventItem;
import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.model.ItemStatus;
import com.rpmedia.backend.repository.EventItemRepository;
import com.rpmedia.backend.repository.EventRepository;
import com.rpmedia.backend.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class EquipmentCartService {

  private final EventRepository eventRepo;
  private final ItemRepository itemRepo;
  private final EventItemRepository eventItemRepo;
  private final EventHistoryService eventHistoryService;

  public EquipmentCartService(EventRepository eventRepo, ItemRepository itemRepo, EventItemRepository eventItemRepo,
      EventHistoryService eventHistoryService) {
    this.eventRepo = eventRepo;
    this.itemRepo = itemRepo;
    this.eventItemRepo = eventItemRepo;
    this.eventHistoryService = eventHistoryService;
  }

  // ---------- helpers ----------
  private static int daysInclusive(LocalDate s, LocalDate e) {
    return (int) ChronoUnit.DAYS.between(s, e) + 1;
  }

  private static BigDecimal nz(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static int nzi(Integer v) {
    return v == null ? 0 : v;
  }

  // getter/setter แบบกันทั้ง camelCase/snake_case
  private static BigDecimal getUnitPrice(EventItem ei) {
    try {
      return (BigDecimal) ei.getClass().getMethod("getUnitPrice").invoke(ei);
    } catch (Exception ignore) {
      try {
        return (BigDecimal) ei.getClass().getMethod("getUnit_price").invoke(ei);
      } catch (Exception ex) {
        return BigDecimal.ZERO;
      }
    }
  }

  private static void setUnitPrice(EventItem ei, BigDecimal v) {
    try {
      ei.getClass().getMethod("setUnitPrice", BigDecimal.class).invoke(ei, v);
    } catch (Exception ignore) {
      try {
        ei.getClass().getMethod("setUnit_price", BigDecimal.class).invoke(ei, v);
      } catch (Exception ex) {
      }
    }
  }

  private static String getRateType(EventItem ei) {
    try {
      return (String) ei.getClass().getMethod("getRateType").invoke(ei);
    } catch (Exception ignore) {
      try {
        return (String) ei.getClass().getMethod("getRate_type").invoke(ei);
      } catch (Exception ex) {
        return "PER_DAY";
      }
    }
  }

  private static void setRateType(EventItem ei, String v) {
    try {
      ei.getClass().getMethod("setRateType", String.class).invoke(ei, v);
    } catch (Exception ignore) {
      try {
        ei.getClass().getMethod("setRate_type", String.class).invoke(ei, v);
      } catch (Exception ex) {
      }
    }
  }

  private static void setLineTotal(EventItem ei, BigDecimal v) {
    try {
      ei.getClass().getMethod("setLineTotal", BigDecimal.class).invoke(ei, v);
    } catch (Exception ignore) {
      try {
        ei.getClass().getMethod("setLine_total", BigDecimal.class).invoke(ei, v);
      } catch (Exception ex) {
      }
    }
  }

  private void recalcLineTotal(EventItem ei) {
    Event ev = ei.getEvent();
    int days = daysInclusive(ev.getStartDate(), ev.getEndDate());
    BigDecimal unit = getUnitPrice(ei);
    if (unit == null || unit.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalStateException("UNIT_PRICE_REQUIRED");
    }
    String rateType = getRateType(ei);
    BigDecimal qty = BigDecimal.valueOf(nzi(ei.getRequestedQuantity()));
    BigDecimal line = "PER_DAY".equalsIgnoreCase(rateType)
        ? unit.multiply(qty).multiply(BigDecimal.valueOf(days))
        : unit.multiply(qty);
    setLineTotal(ei, line.setScale(2, RoundingMode.HALF_UP));
  }

  // ---------- commands/queries ----------

  @Transactional
  public EventItem addOrUpdate(Long eventId, AddEventItemRequest req) {
    Event ev = eventRepo.findById(eventId).orElseThrow(() -> new RuntimeException("Event not found"));
    Item it = itemRepo.findById(req.itemId()).orElseThrow(() -> new RuntimeException("Item not found"));

    // guard: qty
    if (nzi(req.requestedQuantity()) <= 0) {
      throw new IllegalStateException("REQUESTED_QTY_REQUIRED");
    }

    // default returnDate = endDate (กัน null)
    LocalDate returnDate = (req.returnDate() != null) ? req.returnDate() : ev.getEndDate();

    // validate availability
    Integer available = itemRepo.availableForItem(it.getId(), ev.getId(), ev.getStartDate(), ev.getEndDate());
    if (available < nzi(req.requestedQuantity())) {
      // Phase A: ยังบล็อกไว้ก่อน (Phase B จะเปิด allowOverbook)
      throw new IllegalStateException("QUANTITY_EXCEEDS_AVAILABLE:" + available);
    }

    // upsert
    EventItem ei = eventItemRepo.findByEventIdAndItemId(eventId, it.getId())
        .orElseGet(() -> {
          EventItem x = new EventItem();
          x.setEvent(ev);
          x.setItem(it);
          return x;
        });

    ei.setRequestedQuantity(req.requestedQuantity());
    ei.setReturnDate(returnDate);

    // ใช้ราคา/เรทจากผู้ใช้
    BigDecimal unit = nz(req.unitPrice()).setScale(2, RoundingMode.HALF_UP);
    if (unit.compareTo(BigDecimal.ZERO) <= 0)
      throw new IllegalStateException("UNIT_PRICE_REQUIRED");
    String rateType = (req.rateType() == null || req.rateType().isBlank()) ? "PER_DAY" : req.rateType().toUpperCase();

    setUnitPrice(ei, unit);
    setRateType(ei, rateType);

    recalcLineTotal(ei);

    if (ei.getStatus() == null || ei.getStatus() == ItemStatus.DRAFT) {
      ei.setStatus(ItemStatus.HOLD);
    }

    return eventItemRepo.save(ei);
  }

  @Transactional(readOnly = true)
  public List<EventItem> list(Long eventId) {
    return eventItemRepo.findByEventId(eventId);
  }

  @Transactional
  public EventItem update(Long eventId, Long eventItemId, AddEventItemRequest req) {
    EventItem ei = eventItemRepo.findById(eventItemId)
        .orElseThrow(() -> new RuntimeException("EventItem not found"));
    if (!ei.getEvent().getId().equals(eventId))
      throw new RuntimeException("Event mismatch");

    if (req.requestedQuantity() != null) {
      if (nzi(req.requestedQuantity()) <= 0)
        throw new IllegalStateException("REQUESTED_QTY_REQUIRED");
      ei.setRequestedQuantity(req.requestedQuantity());
    }
    if (req.returnDate() != null) {
      ei.setReturnDate(req.returnDate());
    }
    if (req.unitPrice() != null) {
      BigDecimal unit = nz(req.unitPrice()).setScale(2, RoundingMode.HALF_UP);
      if (unit.compareTo(BigDecimal.ZERO) <= 0)
        throw new IllegalStateException("UNIT_PRICE_REQUIRED");
      setUnitPrice(ei, unit);
    }
    if (req.rateType() != null && !req.rateType().isBlank()) {
      setRateType(ei, req.rateType().toUpperCase());
    }

    recalcLineTotal(ei);
    return eventItemRepo.save(ei);
  }

  @Transactional
  public void remove(Long eventId, Long eventItemId) {
    EventItem ei = eventItemRepo.findById(eventItemId)
        .orElseThrow(() -> new RuntimeException("EventItem not found"));
    if (!ei.getEvent().getId().equals(eventId))
      throw new RuntimeException("Event mismatch");
    eventItemRepo.delete(ei);
  }

  // quote(): ไม่มี VAT/ServiceFee — สรุป subtotal อย่างเดียว
  @Transactional(readOnly = true)
  public QuoteDTO quote(Long eventId) {
    List<EventItem> items = eventItemRepo.findByEventId(eventId);
    var rows = items.stream().map(ei -> {
      int days = daysInclusive(ei.getEvent().getStartDate(), ei.getEvent().getEndDate());
      BigDecimal unit = getUnitPrice(ei);
      BigDecimal line;
      try {
        line = (BigDecimal) ei.getClass().getMethod("getLineTotal").invoke(ei);
      } catch (Exception ignore) {
        try {
          line = (BigDecimal) ei.getClass().getMethod("getLine_total").invoke(ei);
        } catch (Exception ex) {
          line = BigDecimal.ZERO;
        }
      }
      String rateType = getRateType(ei);

      return new QuoteItemDTO(
          ei.getId(), ei.getItem().getId(), ei.getItem().getName(),
          nzi(ei.getRequestedQuantity()), days, rateType,
          unit.setScale(2, RoundingMode.HALF_UP),
          line.setScale(2, RoundingMode.HALF_UP));
    }).toList();

    BigDecimal subtotal = rows.stream()
        .map(QuoteItemDTO::lineTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, RoundingMode.HALF_UP);

    return new QuoteDTO(rows, subtotal);
  }

  @Transactional
  public void confirm(Long eventId) {
    eventItemRepo.findByEventId(eventId).forEach(ei -> {
      if (ei.getStatus() != ItemStatus.REJECTED) {
        ei.setStatus(ItemStatus.CONFIRMED);
      }
    });
    // Log to history
    eventHistoryService.log(eventId, 0L, "CONFIRM_CART", "Manager confirmed items from equipment cart");
  }
}
