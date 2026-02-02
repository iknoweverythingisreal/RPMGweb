package com.rpmedia.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpmedia.backend.dto.*;
import com.rpmedia.backend.model.*;
import com.rpmedia.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventItemService {

    private final EventItemRepository eventItemRepository;
    private final EventRepository eventRepository;
    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final EventItemUnitRepository eventItemUnitRepository;
    private final EventHistoryService eventHistoryService;
    private final UnifiedAvailabilityService unifiedAvailabilityService;
    private final ObjectMapper objectMapper;

    public List<EventItemDTO> getAllEventItems() {
        return eventItemRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public Optional<EventItemDTO> getEventItemById(Long id) {
        return eventItemRepository.findById(id).map(this::toDto);
    }

    public List<EventItemDTO> getByEventId(Long eventId) {
        return eventItemRepository.findByEventId(eventId).stream()
                .map(this::toDto)
                .toList();
    }

    public EventItem create(EventItem eventItem) {
        return eventItemRepository.save(eventItem);
    }

    public EventItem update(Long id, EventItem updated) {
        return eventItemRepository.findById(id).map(existing -> {
            existing.setEvent(updated.getEvent());
            existing.setItem(updated.getItem());
            existing.setRequestedQuantity(updated.getRequestedQuantity());
            existing.setAllocatedQuantity(updated.getAllocatedQuantity());
            existing.setStatus(updated.getStatus());
            existing.setUnitPrice(updated.getUnitPrice());
            existing.setRateType(updated.getRateType());
            existing.setLineTotal(updated.getLineTotal());
            existing.setRemark(updated.getRemark());
            existing.setOverbookQty(updated.getOverbookQty());
            existing.setOverbookStatus(updated.getOverbookStatus());
            existing.setOverbookNote(updated.getOverbookNote());
            existing.setOverbookApprovedBy(updated.getOverbookApprovedBy());
            existing.setOverbookApprovedAt(updated.getOverbookApprovedAt());
            existing.setMetadata(updated.getMetadata());
            return eventItemRepository.save(existing);
        }).orElseThrow(() -> new RuntimeException("EventItem not found"));
    }

    @Transactional
    public void delete(Long id) {
        eventItemRepository.findById(id).ifPresent(ei -> {
            Long eventId = ei.getEvent().getId();
            String itemName = ei.getItem().getName();
            eventItemRepository.delete(ei);
            eventHistoryService.log(eventId, getCurrentUserId(), "ITEM_REMOVED", "Removed item: " + itemName);
        });
    }

    public List<EventItem> findByOverbookStatus(OverbookStatus status) {
        return eventItemRepository.findByOverbookStatus(status);
    }

    public List<EventItem> findPendingRentals() {
        return eventItemRepository.findPendingRentals();
    }

    public EventItem approveOverbook(Long id, Long approverId, String note) {
        return eventItemRepository.findById(id).map(item -> {
            item.setOverbookStatus(OverbookStatus.APPROVED);
            item.setOverbookNote(note);
            item.setOverbookApprovedBy(approverId);
            item.setOverbookApprovedAt(LocalDateTime.now());
            return eventItemRepository.save(item);
        }).orElseThrow(() -> new RuntimeException("EventItem not found"));
    }

    public EventItem rejectOverbook(Long id, Long approverId, String note) {
        return eventItemRepository.findById(id).map(item -> {
            item.setOverbookStatus(OverbookStatus.REJECTED);
            item.setOverbookNote(note);
            item.setOverbookApprovedBy(approverId);
            item.setOverbookApprovedAt(LocalDateTime.now());
            return eventItemRepository.save(item);
        }).orElseThrow(() -> new RuntimeException("EventItem not found"));
    }

    private void createEventItemUnits(EventItem eventItem, List<String> serials) {
        List<EventItemUnit> units = new ArrayList<>();
        for (String serial : serials) {
            EventItemUnit u = new EventItemUnit();
            u.setEventItem(eventItem);
            ItemUnit itemUnit = itemUnitRepository.findBySerial(serial);
            u.setItemUnit(itemUnit);
            u.setStatus("PICKED");
            units.add(u);
        }
        eventItemUnitRepository.saveAll(units);
    }

    public EventItemDTO addSingleItem(EventItemRequestDTO dto) {
        Event event = eventRepository.findById(dto.getEventId())
                .orElseThrow(() -> new RuntimeException("Event not found"));
        Item item = itemRepository.findById(dto.getItemId())
                .orElseThrow(() -> new RuntimeException("Item not found"));

        EventItem entity = new EventItem();
        entity.setEvent(event);
        entity.setItem(item);

        boolean hasSerial = dto.getSerials() != null && !dto.getSerials().isEmpty();
        if (hasSerial) {
            validateSerialMode(dto);
            int serialCount = dto.getSerials().size();
            entity.setRequestedQuantity(serialCount);
            entity.setAllocatedQuantity(serialCount);
            entity.setSerials(objectMapper.valueToTree(dto.getSerials()));
        } else {
            int reqQty = dto.getRequestedQuantity() != null ? dto.getRequestedQuantity().intValue() : 0;
            entity.setRequestedQuantity(reqQty);
            entity.setAllocatedQuantity(0);
        }

        entity.setUnitPrice(dto.getUnitPrice() != null ? dto.getUnitPrice() : BigDecimal.ZERO);
        entity.setRateType(dto.getRateType() != null ? dto.getRateType() : "NONE");
        entity.setLineTotal(entity.getUnitPrice().multiply(BigDecimal.valueOf(entity.getRequestedQuantity())));
        entity.setStatus(ItemStatus.DRAFT);
        entity.setRemark(dto.getRemark());
        entity.setOverbookQty(0);
        entity.setOverbookStatus(OverbookStatus.NONE);
        entity.setOverbookNote(dto.getOverbookNote());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        EventItem saved = eventItemRepository.save(entity);
        if (hasSerial) {
            createEventItemUnits(saved, dto.getSerials());
        }
        return toDto(saved);
    }

    @Transactional
    public BulkEventItemResponse addBulkItemsToEvent(Long eventId, List<EventItemRequestDTO> requests) {
        System.out.println(">>> [SERVICE] ENTERING addBulkItemsToEvent for eventId: " + eventId);
        List<EventItemDTO> results = new ArrayList<>();
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        for (EventItemRequestDTO req : requests) {
            // 🔹 Check availability (Phase 17: Support Overbooking)
            UnifiedAvailabilityDTO avail = unifiedAvailabilityService.compute(
                    req.getItemId(),
                    eventId,
                    event.getStartDate(),
                    event.getEndDate());

            int requestedQty = req.getRequestedQuantity();
            int available = avail.getAvailable();
            int overbookQty = 0;
            OverbookStatus overbookStatus = OverbookStatus.NONE;

            if (available < requestedQty) {
                overbookQty = requestedQty - available;
                overbookStatus = OverbookStatus.PENDING;
            }

            Item item = itemRepository.findById(req.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found: " + req.getItemId()));

            // 🔹 Check if item already exists in event
            // Allow multiple entries if it's a SERVICE_ITEM (for external rentals)
            boolean isServiceItem = "SERVICE_ITEM".equals(item.getDescription());
            if (!isServiceItem && eventItemRepository.existsByEventIdAndItemId(eventId, req.getItemId())) {
                System.out.println("[DEBUG] Item " + req.getItemId() + " already exists. Skipping.");
                continue;
            }

            EventItem entity = new EventItem();
            entity.setEvent(event);
            entity.setItem(item);

            boolean hasSerial = req.getSerials() != null && !req.getSerials().isEmpty();
            if (hasSerial) {
                int sc = req.getSerials().size();
                entity.setRequestedQuantity(sc);
                entity.setAllocatedQuantity(sc);
                entity.setSerials(objectMapper.valueToTree(req.getSerials()));
            } else {
                entity.setRequestedQuantity(requestedQty);
                // Fix: Allocated quantity should be what's actually available, capped at
                // requested
                // For service items (External Rental), we don't allocate from internal stock
                if (isServiceItem) {
                    entity.setAllocatedQuantity(0);
                } else {
                    entity.setAllocatedQuantity(Math.min(requestedQty, Math.max(0, available)));
                }
            }

            entity.setUnitPrice(req.getUnitPrice() != null ? req.getUnitPrice() : BigDecimal.ZERO);
            entity.setRateType(req.getRateType() != null ? req.getRateType() : "NONE");
            entity.setLineTotal(entity.getUnitPrice().multiply(BigDecimal.valueOf(entity.getRequestedQuantity())));
            entity.setRemark(req.getRemark());

            // Fix: Set status to PENDING_RENT for External Rentals to trigger approval flow
            if (isServiceItem) {
                entity.setStatus(ItemStatus.PENDING_RENT);
                entity.setSource("RENT_EXTERNAL");
            } else {
                entity.setStatus(ItemStatus.CONFIRMED);
            }

            entity.setOverbookQty(overbookQty);
            entity.setOverbookStatus(overbookStatus);
            entity.setOverbookNote(req.getOverbookNote());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());

            EventItem saved = eventItemRepository.save(entity);
            if (hasSerial) {
                createEventItemUnits(saved, req.getSerials());
            }

            // Log item addition using independent transaction
            try {
                eventHistoryService.log(eventId, getCurrentUserId(), "ITEM_ADDED",
                        "Added item: " + saved.getItem().getName() + " (Qty: " + saved.getRequestedQuantity() + ")");
            } catch (Exception e) {
                System.err.println("⚠ Failed to log item addition: " + e.getMessage());
            }

            results.add(toDto(saved));
        }

        if (!results.isEmpty()) {
            try {
                eventHistoryService.log(eventId, getCurrentUserId(), "ITEMS_BOOKED",
                        "Booked " + results.size() + " items to event");
            } catch (Exception e) {
                System.err.println("⚠ Failed to log items booked: " + e.getMessage());
            }
        }

        return BulkEventItemResponse.builder()
                .eventId(eventId)
                .items(results)
                .build();
    }

    @Transactional
    public void updateQuantity(Long eventItemId, int newQuantity) {
        EventItem eventItem = eventItemRepository.findById(eventItemId)
                .orElseThrow(() -> new RuntimeException("EventItem not found"));
        int oldQuantity = eventItem.getRequestedQuantity();
        eventItem.setRequestedQuantity(newQuantity);
        eventItem.setAllocatedQuantity(newQuantity);
        eventItem.setUpdatedAt(LocalDateTime.now());
        eventItem.setLineTotal(eventItem.getUnitPrice().multiply(BigDecimal.valueOf(newQuantity)));
        eventItemRepository.save(eventItem);

        eventHistoryService.log(eventItem.getEvent().getId(), getCurrentUserId(), "ITEM_QTY_UPDATED",
                "Updated quantity for " + eventItem.getItem().getName() + " from " + oldQuantity + " to "
                        + newQuantity);
    }

    @Transactional
    public int markPrepared(Long eventId, Long preparedBy) {
        var items = eventItemRepository.findByEventId(eventId);
        for (var ei : items) {
            if (ei.getStatus() == ItemStatus.CONFIRMED) {
                ei.setStatus(ItemStatus.READY);
                ei.setPreparedBy(preparedBy);
                ei.setPreparedAt(LocalDateTime.now());
            }
        }
        eventItemRepository.saveAll(items);
        eventHistoryService.log(eventId, preparedBy, "PREPARE", "Technical marked items as READY");
        return items.size();
    }

    @Transactional
    public int markChecked(Long eventId, Long checkedBy) {
        var items = eventItemRepository.findByEventId(eventId);
        for (var ei : items) {
            if (ei.getStatus() == ItemStatus.READY) {
                ei.setStatus(ItemStatus.CHECKED);
                ei.setCheckedBy(checkedBy);
                ei.setCheckedAt(LocalDateTime.now());
            }
        }
        eventItemRepository.saveAll(items);
        eventHistoryService.log(eventId, checkedBy, "CHECK", "Technical checked and verified items");
        return items.size();
    }

    @Transactional
    public void requestRentExternal(Long eventId, Long requesterId, Long itemId, Double qty, String reason) {
        EventItem ei = eventItemRepository.findByEventIdAndItemId(eventId, itemId).orElseGet(() -> {
            EventItem newItem = new EventItem();
            newItem.setEvent(eventRepository.findById(eventId).orElseThrow());
            newItem.setItem(itemRepository.findById(itemId).orElseThrow());
            newItem.setRequestedQuantity(qty.intValue());
            newItem.setAllocatedQuantity(0);
            newItem.setUnitPrice(BigDecimal.ZERO);
            newItem.setRateType("NONE");
            newItem.setLineTotal(BigDecimal.ZERO);
            newItem.setCreatedAt(LocalDateTime.now());
            return newItem;
        });

        ei.setSource("RENT_EXTERNAL");
        ei.setStatus(ItemStatus.PENDING_RENT);
        ei.setRemark(reason);
        ei.setUpdatedAt(LocalDateTime.now());
        eventItemRepository.save(ei);

        eventHistoryService.log(eventId, requesterId, "RENT_REQUEST",
                "Request rent for item: " + ei.getItem().getName() + " (Qty: " + qty + ")");
    }

    @Transactional
    public void approveRentExternal(Long eventItemId, Long approverId, boolean approved, String note) {
        eventItemRepository.findById(eventItemId).ifPresent(ei -> {
            if (ei.getStatus() == ItemStatus.PENDING_RENT || ei.getStatus() == ItemStatus.REQUESTED) {
                ei.setStatus(approved ? ItemStatus.CONFIRMED : ItemStatus.REJECTED);
                ei.setRemark(note);
                ei.setConfirmedBy(approverId);
                ei.setUpdatedAt(LocalDateTime.now());
                eventItemRepository.save(ei);
                eventHistoryService.log(ei.getEvent().getId(), approverId, approved ? "RENT_APPROVED" : "RENT_REJECTED",
                        "Rent " + (approved ? "approved" : "rejected") + " for " + ei.getItem().getName() + ". Note: "
                                + note);
            }
        });
    }

    @Transactional
    public int confirmEventItems(Long eventId, Long confirmedBy) {
        var items = eventItemRepository.findByEventId(eventId);
        for (var ei : items) {
            if (ei.getStatus() == ItemStatus.DRAFT || ei.getStatus() == ItemStatus.REQUESTED) {
                ei.setStatus(ItemStatus.CONFIRMED);
                ei.setConfirmedBy(confirmedBy);
            }
        }
        eventItemRepository.saveAll(items);
        eventHistoryService.log(eventId, confirmedBy, "CONFIRM", "Manager confirmed all items");
        return items.size();
    }

    @Transactional
    public EventItem reserveItem(Long eventId, Long itemId, int qty, Long userId) {
        EventItem ei = eventItemRepository.findByEventIdAndItemId(eventId, itemId).orElseGet(() -> {
            EventItem newItem = new EventItem();
            newItem.setEvent(new Event(eventId));
            newItem.setItem(new Item(itemId));
            return newItem;
        });
        ei.setRequestedQuantity(qty);
        ei.setAllocatedQuantity(qty);
        ei.setStatus(ItemStatus.REQUESTED);
        EventItem saved = eventItemRepository.save(ei);
        eventHistoryService.log(eventId, userId, "RESERVE_ITEM", "Reserved " + qty + " of item #" + itemId);
        return saved;
    }

    @Transactional
    public void confirmReservedItems(Long eventId, Long confirmedBy) {
        var items = eventItemRepository.findByEventId(eventId);
        for (EventItem ei : items) {
            if (ei.getStatus() == ItemStatus.DRAFT || ei.getStatus() == ItemStatus.REQUESTED) {
                ei.setStatus(ItemStatus.CONFIRMED);
                ei.setConfirmedBy(confirmedBy);
            }
        }
        eventItemRepository.saveAll(items);
        eventHistoryService.log(eventId, confirmedBy, "CONFIRM_ITEMS", "Manager confirmed reserved items");
    }

    @Transactional
    public int requestReturn(Long eventId, Long requesterId) {
        var items = eventItemRepository.findByEventId(eventId);
        int count = 0;
        for (var ei : items) {
            if (ei.getStatus() == ItemStatus.CHECKED) {
                ei.setStatus(ItemStatus.RETURN_REQUESTED);
                count++;
            }
        }
        eventItemRepository.saveAll(items);
        if (count > 0)
            eventHistoryService.log(eventId, requesterId, "RETURN_REQUEST", "Requested return for " + count + " items");
        return count;
    }

    @Transactional
    public int approveReturn(Long eventId, Long approverId) {
        var items = eventItemRepository.findByEventId(eventId);
        int count = 0;
        for (var ei : items) {
            if (ei.getStatus() == ItemStatus.RETURN_REQUESTED) {
                ei.setStatus(ItemStatus.RETURNED);
                var units = eventItemUnitRepository.findByEventItemId(ei.getId());
                for (var u : units) {
                    u.setStatus("RETURNED");
                    u.setReturnedAt(LocalDateTime.now());
                }
                eventItemUnitRepository.saveAll(units);
                count++;
            }
        }
        eventItemRepository.saveAll(items);
        if (count > 0)
            eventHistoryService.log(eventId, approverId, "RETURN_APPROVED", "Approved return for " + count + " items");
        return count;
    }

    public EventItemDTO toDto(EventItem ei) {
        String itemName = ei.getItem().getName();
        if (itemName == null || itemName.trim().isEmpty()) {
            itemName = ei.getItem().getModel();
        }
        if (itemName == null || itemName.trim().isEmpty()) {
            itemName = ei.getItem().getDescription();
        }
        if (itemName == null || itemName.trim().isEmpty()) {
            itemName = "Item #" + ei.getItem().getId();
        }

        return EventItemDTO.builder()
                .id(ei.getId())
                .eventId(ei.getEvent().getId())
                .eventName(ei.getEvent().getTitle())
                .itemId(ei.getItem().getId())
                .itemName(itemName)
                .category(ei.getItem().getCategory())
                .brand(ei.getItem().getBrand())
                .model(ei.getItem().getModel())
                .uom(ei.getItem().getUom())
                .requestedQuantity(BigDecimal.valueOf(ei.getRequestedQuantity()))
                .allocatedQuantity(BigDecimal.valueOf(ei.getAllocatedQuantity()))
                .unitPrice(ei.getUnitPrice())
                .rateType(ei.getRateType())
                .lineTotal(ei.getLineTotal())
                .status(ei.getStatus().name())
                .overbookQty(BigDecimal.valueOf(ei.getOverbookQty()))
                .overbookStatus(ei.getOverbookStatus().name())
                .overbookNote(ei.getOverbookNote())
                .overbookApprovedBy(ei.getOverbookApprovedBy())
                .overbookApprovedAt(ei.getOverbookApprovedAt())
                .remark(ei.getRemark())
                .build();
    }

    private Long getCurrentUserId() {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.rpmedia.backend.model.User) {
                return ((com.rpmedia.backend.model.User) auth.getPrincipal()).getId();
            }
        } catch (Exception e) {
            System.err.println("⚠ Failed to get current user ID: " + e.getMessage());
        }
        return 15L; // Fallback to admin user (ID 15)
    }

    private void validateSerialMode(EventItemRequestDTO dto) {
        List<String> serials = dto.getSerials();
        if (serials == null || serials.isEmpty())
            throw new IllegalArgumentException("Serial list cannot be empty");
        if (serials.size() != new HashSet<>(serials).size())
            throw new IllegalArgumentException("Duplicate serials detected");
        List<ItemUnit> foundUnits = itemUnitRepository.findValidSerials(dto.getItemId(), serials);
        if (foundUnits.size() != serials.size())
            throw new IllegalArgumentException("Invalid serials for item");
    }
}
