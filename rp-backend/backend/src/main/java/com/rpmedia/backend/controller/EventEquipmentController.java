package com.rpmedia.backend.controller;

import com.rpmedia.backend.dto.AddEventItemRequest;
import com.rpmedia.backend.dto.QuoteDTO;
import com.rpmedia.backend.model.EventItem;
import com.rpmedia.backend.service.EquipmentCartService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events/{eventId}/equipment")
public class EventEquipmentController {

  private final EquipmentCartService svc;

  public EventEquipmentController(EquipmentCartService svc) {
    this.svc = svc;
  }

  @PostMapping
  public EventItem addOrUpdate(@PathVariable("eventId") Long eventId, @RequestBody AddEventItemRequest req) {
    return svc.addOrUpdate(eventId, req);
  }

  @GetMapping
  public List<EventItem> list(@PathVariable("eventId") Long eventId) {
    return svc.list(eventId);
  }

  @PutMapping("/{eventItemId}")
  public EventItem update(@PathVariable("eventId") Long eventId,
      @PathVariable("eventItemId") Long eventItemId,
      @RequestBody AddEventItemRequest req) {
    return svc.update(eventId, eventItemId, req);
  }

  @DeleteMapping("/{eventItemId}")
  public void remove(@PathVariable("eventId") Long eventId, @PathVariable("eventItemId") Long eventItemId) {
    svc.remove(eventId, eventItemId);
  }

  @GetMapping("/quote")
  public QuoteDTO quote(@PathVariable("eventId") Long eventId) {
    return svc.quote(eventId); // ไม่มี VAT/ServiceFee
  }

  @PostMapping("/confirm")
  public void confirm(@PathVariable("eventId") Long eventId) {
    svc.confirm(eventId);
  }
}
