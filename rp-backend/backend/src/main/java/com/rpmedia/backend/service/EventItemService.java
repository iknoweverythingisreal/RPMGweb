package com.rpmedia.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpmedia.backend.dto.*;
import com.rpmedia.backend.model.*;
import com.rpmedia.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final EntityManager entityManager;

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

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
    public EventItemDTO delete(Long id) {
        return eventItemRepository.findById(id).map(ei -> {
            Long eventId = ei.getEvent().getId();
            String itemName = ei.getItem().getName();
            EventItemDTO dto = toDto(ei);
            eventItemRepository.delete(ei);
            eventHistoryService.log(eventId, getCurrentUserId(), "ITEM_REMOVED", "Removed item: " + itemName);
            return dto;
        }).orElseThrow(() -> new RuntimeException("EventItem not found"));
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
        entity.setLineTotal(entity.getUnitPrice().multiply(BigDecimal.valueOf(nvl(entity.getRequestedQuantity()))));
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
            // 🔹 CONCURRENCY: Lock the item to prevent race conditions during availability
            // check/booking
            Item item = itemRepository.findByIdWithLock(req.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found: " + req.getItemId()));

            // 🔹 Check availability (Phase 17: Support Overbooking)
            UnifiedAvailabilityDTO avail = unifiedAvailabilityService.compute(
                    req.getItemId(),
                    null, // Include current event's existing bookings in "available" calculation
                    event.getStartDate(),
                    event.getEndDate());

            int requestedQty = nvl(req.getRequestedQuantity());
            int available = avail.getAvailable();

            boolean hasSerial = req.getSerials() != null && !req.getSerials().isEmpty();

            // 🔹 Calculate and Split Stock vs Rental
            // If the incoming request explicitly marks this as a rental source, force it to
            // be a rental
            boolean forceRental = "RENT_EXTERNAL".equals(req.getSource());

            // FIX: For non-serial items, we ALWAYS want to allocate to stock to ensure
            // global deduction
            // We only split if it's a serial item (physical constraint) or if forceRental
            // is true.
            int stockQty;
            int rentalQty;

            if (hasSerial || forceRental) {
                // Serial items or Forced Rental still use the split logic
                stockQty = forceRental ? 0 : Math.min(requestedQty, Math.max(0, available));
                rentalQty = requestedQty - stockQty;
            } else {
                // Non-serial stock items: ALWAYS allocate full requested qty to stock pool
                stockQty = requestedQty;
                rentalQty = 0;
            }

            // Try to handle Stock portion
            if (stockQty > 0) {
                handleSplitMerging(event, item, stockQty, stockQty, req, results, hasSerial, available);
            }

            // Try to handle Rental/Shortage portion
            if (rentalQty > 0) {
                handleSplitMerging(event, item, rentalQty, 0, req, results, false, available); // No serials for
                                                                                               // shortages
            }
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
        int oldQuantity = nvl(eventItem.getRequestedQuantity());
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
        // Production Fix: Find "External Rental" item if itemId is 0 (generic)
        // Fallback: If not found, use any SERVICE item.
        Item item = null;
        if (itemId != null && itemId > 0) {
            item = itemRepository.findById(itemId).orElse(null);
        }

        if (item == null) {
            item = itemRepository.findByName("External Rental").orElse(null);
            if (item == null) {
                // Try case-insensitive matching if exact name fails
                List<Item> matches = itemRepository.findByNameContainingIgnoreCase("External Rental");
                if (!matches.isEmpty())
                    item = matches.get(0);
            }
            if (item == null) {
                // Try by category
                List<Item> rentals = itemRepository.findByCategoryIgnoreCase("RENTAL");
                if (!rentals.isEmpty())
                    item = rentals.get(0);
            }
            // Last resort: find ANY item to attach to avoid 500 error
            if (item == null) {
                item = itemRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new RuntimeException(
                                "System Error: No strict 'External Rental' item found. Please create one."));
            }
        }

        EventItem ei = new EventItem();
        ei.setEvent(eventRepository.findById(eventId).orElseThrow(() -> new RuntimeException("Event not found")));
        ei.setItem(item);
        ei.setRequestedQuantity(qty.intValue());
        ei.setAllocatedQuantity(0);
        ei.setUnitPrice(BigDecimal.ZERO);
        ei.setRateType("NONE");
        ei.setLineTotal(BigDecimal.ZERO);
        ei.setCreatedAt(LocalDateTime.now());

        ei.setSource("RENT_EXTERNAL");
        ei.setStatus(ItemStatus.PENDING_RENT);
        ei.setRemark(reason);
        ei.setUpdatedAt(LocalDateTime.now());
        eventItemRepository.save(ei);

        eventHistoryService.log(eventId, requesterId, "RENT_REQUEST",
                "Request rent: " + reason + " (Qty: " + qty + ")");
    }

    @Transactional
    public void approveRentExternal(Long eventItemId, Long approverId, boolean approved, String note) {
        eventItemRepository.findById(eventItemId).ifPresent(ei -> {
            if (ei.getStatus() == ItemStatus.PENDING_RENT || ei.getStatus() == ItemStatus.REQUESTED) {
                ei.setStatus(approved ? ItemStatus.CONFIRMED : ItemStatus.REJECTED);
                ei.setRemark(note);
                ei.setConfirmedBy(approverId);
                ei.setUpdatedAt(LocalDateTime.now());

                // 🔹 Unify Overbook Status if this was an auto-rental from overbooking
                if (ei.getOverbookQty() != null && ei.getOverbookQty() > 0) {
                    ei.setOverbookStatus(approved ? OverbookStatus.APPROVED : OverbookStatus.REJECTED);
                    ei.setOverbookApprovedBy(approverId);
                    ei.setOverbookApprovedAt(LocalDateTime.now());
                    ei.setOverbookNote(note);
                }

                eventItemRepository.save(ei);
                eventHistoryService.log(ei.getEvent().getId(), approverId, approved ? "RENT_APPROVED" : "RENT_REJECTED",
                        "Rent " + (approved ? "approved" : "rejected") + " for " + ei.getItem().getName() + ". Note: "
                                + note);
            }
        });
    }

    @Transactional
    public int confirmEventItems(Long eventId, Long confirmedBy) {
        System.out.println(">>> [CONFIRM_BOOKING] EventID=" + eventId + ", By=" + confirmedBy);
        var items = eventItemRepository.findByEventId(eventId);

        try {
            for (var ei : items) {
                if (ei.getStatus() == ItemStatus.DRAFT || ei.getStatus() == ItemStatus.REQUESTED) {
                    ei.setStatus(ItemStatus.CONFIRMED);
                    ei.setConfirmedBy(confirmedBy);
                    ei.setUpdatedAt(LocalDateTime.now());
                }
            }

            System.out.println("  [SAVE] Saving " + items.size() + " confirmed items...");
            eventItemRepository.saveAll(items);

            System.out.println("  [FLUSH] Explicitly flushing to catch DB errors...");
            entityManager.flush();

            System.out.println("  [LOG] Adding history entry...");
            eventHistoryService.log(eventId, confirmedBy, "CONFIRM", "Manager confirmed all items");

            return items.size();
        } catch (Exception e) {
            System.err.println("❌ ERROR during confirmEventItems: " + e.getMessage());
            e.printStackTrace();

            // Detailed message for frontend
            String detail = e.getMessage();
            if (detail != null && detail.contains("column")) {
                throw new RuntimeException("Database schema mismatch: " + detail);
            }
            throw new RuntimeException("Finalize failed: " + detail);
        }
    }

    @Transactional
    public EventItem reserveItem(Long eventId, Long itemId, int qty, Long userId) {
        List<EventItem> list = eventItemRepository.findByEventIdAndItemId(eventId, itemId);
        EventItem ei = list.isEmpty() ? null : list.get(0);

        if (ei == null) {
            ei = new EventItem();
            ei.setEvent(new Event(eventId));
            ei.setItem(itemRepository.findById(itemId).orElseThrow());
        }
        ei.setRequestedQuantity(qty);
        ei.setAllocatedQuantity(0); // Production Fix: Advance Booking should not block stock
        ei.setOverbookQty(qty);
        ei.setOverbookStatus(OverbookStatus.APPROVED);
        ei.setStatus(ItemStatus.PENDING_RENT);
        ei.setSource("RENT_EXTERNAL");
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

        // 🔹 If External Rental, use Remark as Name if generic name is detected
        if ("RENT_EXTERNAL".equals(ei.getSource()) && ei.getRemark() != null && !ei.getRemark().isEmpty()) {
            String lowerName = (itemName != null) ? itemName.toLowerCase() : "";
            if (lowerName.contains("rental") || lowerName.contains("service") || lowerName.contains("external")) {
                itemName = ei.getRemark(); // Use the custom description (Brand Model etc)
            }
        }

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
                .eventStartDate(ei.getEvent().getStartDate() != null ? ei.getEvent().getStartDate().toString() : null)
                .eventEndDate(ei.getEvent().getEndDate() != null ? ei.getEvent().getEndDate().toString() : null)
                .location(ei.getEvent().getLocation())
                .itemId(ei.getItem().getId())
                .itemName(itemName)
                .category(ei.getItem().getCategory())
                .brand(ei.getItem().getBrand())
                .model(ei.getItem().getModel())
                .uom(ei.getItem().getUom())
                .requestedQuantity(BigDecimal.valueOf(nvl(ei.getRequestedQuantity())))
                .allocatedQuantity(BigDecimal.valueOf(nvl(ei.getAllocatedQuantity())))
                .unitPrice(ei.getUnitPrice())
                .rateType(ei.getRateType())
                .lineTotal(ei.getLineTotal())
                .status(ei.getStatus().name())
                .overbookQty(BigDecimal.valueOf(nvl(ei.getOverbookQty())))
                .overbookStatus(ei.getOverbookStatus().name())
                .overbookNote(ei.getOverbookNote())
                .overbookApprovedBy(ei.getOverbookApprovedBy())
                .overbookApprovedAt(ei.getOverbookApprovedAt())
                .remark(ei.getRemark())
                .source(ei.getSource())
                .room(ei.getMetadata() != null && ei.getMetadata().has("room") ? ei.getMetadata().get("room").asText()
                        : null)
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

    @Transactional
    public void swapItems(SwapItemRequestDTO request) {
        // 1. Get Source Event Item
        EventItem sourceEI = eventItemRepository.findById(request.getSourceEventItemId())
                .orElseThrow(() -> new RuntimeException("Source EventItem not found"));
        Event sourceEvent = sourceEI.getEvent();
        Item sourceItem = sourceEI.getItem();

        // 2. Get Replacement Item
        Item targetItem = itemRepository.findById(request.getTargetItemId())
                .orElseThrow(() -> new RuntimeException("Target Item not found"));

        // 3. Category/Type Validation
        // Relaxed to Category check. Ideally strictly matched.
        if (!sourceItem.getCategory().equalsIgnoreCase(targetItem.getCategory())) {
            throw new IllegalArgumentException(
                    "Category Mismatch: " + sourceItem.getCategory() + " vs " + targetItem.getCategory());
        }

        System.out.println("Processing Swap: " + sourceItem.getName() + " -> " + targetItem.getName());

        // 4. Logic Branching
        if (request.getTargetEventId() != null) {
            // === SWAP WITH EVENT ===
            Event targetEvent = eventRepository.findById(request.getTargetEventId())
                    .orElseThrow(() -> new RuntimeException("Target Event not found"));

            EventItem targetEI;
            if (request.getTargetEventItemId() != null) {
                targetEI = eventItemRepository.findById(request.getTargetEventItemId())
                        .orElseThrow(() -> new RuntimeException("Target Event Item record not found"));
            } else {
                targetEI = eventItemRepository.findByEventIdAndItemId(targetEvent.getId(), targetItem.getId())
                        .stream().findFirst().orElse(null);
                if (targetEI == null) {
                    throw new RuntimeException("Target Event does not have the specified item to swap");
                }
            }

            // Determine Qty to swap
            int requestedSwapQty = (request.getQuantity() != null && request.getQuantity() > 0)
                    ? request.getQuantity()
                    : nvl(sourceEI.getRequestedQuantity());

            // Cap at available in source/target
            int actualSwapQtyFromSource = Math.min(requestedSwapQty, nvl(sourceEI.getRequestedQuantity()));
            int actualSwapQtyFromTarget = Math.min(requestedSwapQty, nvl(targetEI.getRequestedQuantity()));

            // NEW: Calculate allocations to move before objects are modified/deleted
            int allocToMoveFromSource = Math.min(actualSwapQtyFromSource, nvl(sourceEI.getAllocatedQuantity()));
            int allocToMoveFromTarget = Math.min(actualSwapQtyFromTarget, nvl(targetEI.getAllocatedQuantity()));

            // EXECUTE SWAP
            // 1. Handle Source (Event A loses the old item)
            if (actualSwapQtyFromSource >= nvl(sourceEI.getRequestedQuantity())) {
                sourceEvent.getEventItems().remove(sourceEI);
                eventItemUnitRepository.deleteByEventItemId(sourceEI.getId());
                eventItemRepository.delete(sourceEI);
            } else {
                sourceEI.setRequestedQuantity(nvl(sourceEI.getRequestedQuantity()) - actualSwapQtyFromSource);
                sourceEI.setAllocatedQuantity(
                        Math.max(0, nvl(sourceEI.getAllocatedQuantity()) - actualSwapQtyFromSource));
                eventItemRepository.save(sourceEI);
            }

            // 2. Handle Target (Event B loses the new item for A)
            if (actualSwapQtyFromTarget >= nvl(targetEI.getRequestedQuantity())) {
                targetEvent.getEventItems().remove(targetEI);
                eventItemUnitRepository.deleteByEventItemId(targetEI.getId());
                eventItemRepository.delete(targetEI);
            } else {
                targetEI.setRequestedQuantity(nvl(targetEI.getRequestedQuantity()) - actualSwapQtyFromTarget);
                targetEI.setAllocatedQuantity(
                        Math.max(0, nvl(targetEI.getAllocatedQuantity()) - actualSwapQtyFromTarget));
                eventItemRepository.save(targetEI);
            }

            entityManager.flush();

            // 3. Create results
            // Source Event A ALWAYS gets the Target Item
            createInternalEventItem(sourceEvent, targetItem, actualSwapQtyFromTarget, allocToMoveFromTarget,
                    "Swapped in " + actualSwapQtyFromTarget + " from Event " + targetEvent.getTitle());

            // Target Event B ONLY gets the Source Item if it's a MUTUAL swap
            boolean isMutual = "MUTUAL".equalsIgnoreCase(request.getSwapMode()) || request.getSwapMode() == null;
            if (isMutual) {
                createInternalEventItem(targetEvent, sourceItem, actualSwapQtyFromSource, allocToMoveFromSource,
                        "Swapped in " + actualSwapQtyFromSource + " from Event " + sourceEvent.getTitle());
            }

            // LOG
            Map<String, Object> logSourceData = new HashMap<>();
            logSourceData.put("originalItemName", sourceItem.getName());
            logSourceData.put("newItemName", targetItem.getName());
            logSourceData.put("quantity", actualSwapQtyFromTarget);
            logSourceData.put("reason",
                    (isMutual ? "Mutual Swap" : "One-way Move") + " with Event " + targetEvent.getTitle());
            logSourceData.put("source", "EVENT");
            logSourceData.put("targetEventId", targetEvent.getId());
            EventHistory h1 = eventHistoryService.logChange(sourceEvent.getId(), "ITEM_SUBSTITUTION", logSourceData);
            h1.setAction("ITEM_SUBSTITUTION");
            eventHistoryService.log(sourceEvent.getId(), request.getUserId(), "ITEM_SUBSTITUTION", h1.getNote());

            if (isMutual) {
                Map<String, Object> logTargetData = new HashMap<>();
                logTargetData.put("originalItemName", targetItem.getName());
                logTargetData.put("newItemName", sourceItem.getName());
                logTargetData.put("quantity", actualSwapQtyFromSource);
                logTargetData.put("reason", "Mutual Swap with Event " + sourceEvent.getTitle());
                logTargetData.put("source", "EVENT");
                logTargetData.put("targetEventId", sourceEvent.getId());
                EventHistory h2 = eventHistoryService.logChange(targetEvent.getId(), "ITEM_SUBSTITUTION",
                        logTargetData);
                h2.setAction("ITEM_SUBSTITUTION");
                eventHistoryService.log(targetEvent.getId(), request.getUserId(), "ITEM_SUBSTITUTION", h2.getNote());
            }
        } else {
            // === SWAP WITH STORAGE ===
            int requestedSwapQty = (request.getQuantity() != null && request.getQuantity() > 0)
                    ? request.getQuantity()
                    : nvl(sourceEI.getRequestedQuantity());

            int actualSwapQty = Math.min(requestedSwapQty, nvl(sourceEI.getRequestedQuantity()));

            // Execute
            if (actualSwapQty >= nvl(sourceEI.getRequestedQuantity())) {
                sourceEvent.getEventItems().remove(sourceEI);
                eventItemUnitRepository.deleteByEventItemId(sourceEI.getId());
                eventItemRepository.delete(sourceEI);
            } else {
                sourceEI.setRequestedQuantity(nvl(sourceEI.getRequestedQuantity()) - actualSwapQty);
                sourceEI.setAllocatedQuantity(Math.max(0, nvl(sourceEI.getAllocatedQuantity()) - actualSwapQty));
                eventItemRepository.save(sourceEI);
            }

            entityManager.flush();

            createInternalEventItem(sourceEvent, targetItem, actualSwapQty, actualSwapQty,
                    "Replaced " + actualSwapQty + "x " + sourceItem.getName() + ". Reason: " + request.getReason());

            // Log
            Map<String, Object> logData = new HashMap<>();
            logData.put("originalItemName", sourceItem.getName());
            logData.put("newItemName", targetItem.getName());
            logData.put("quantity", actualSwapQty);
            logData.put("reason", request.getReason());
            logData.put("source", (request.getTargetType() != null) ? request.getTargetType() : "STORAGE");

            EventHistory h = eventHistoryService.logChange(sourceEvent.getId(), "ITEM_SUBSTITUTION", logData);
            h.setAction("ITEM_SUBSTITUTION");
            h.setNote("Substitution: " + actualSwapQty + "x " + sourceItem.getName() + " -> " + targetItem.getName());
            eventHistoryService.log(sourceEvent.getId(), request.getUserId(), "ITEM_SUBSTITUTION", h.getNote());
        }
    }

    private void createInternalEventItem(Event event, Item item, int qty, int allocatedQty, String remark) {
        // Production Fix: Merge with existing item if it already exists in the event
        List<EventItem> list = eventItemRepository.findByEventIdAndItemId(event.getId(), item.getId());

        boolean incomingIsRental = (allocatedQty == 0);

        EventItem existing = list.stream()
                .filter(ei -> ei.getMetadata() == null || !ei.getMetadata().has("room"))
                .filter(ei -> {
                    boolean existingIsRental = (nvl(ei.getAllocatedQuantity()) == 0);
                    return incomingIsRental == existingIsRental;
                })
                .findFirst().orElse(null);

        if (existing != null) {
            existing.setRequestedQuantity(nvl(existing.getRequestedQuantity()) + qty);
            existing.setAllocatedQuantity(nvl(existing.getAllocatedQuantity()) + allocatedQty);
            existing.setRemark(existing.getRemark() + " | " + remark);
            existing.setUpdatedAt(LocalDateTime.now());
            eventItemRepository.save(existing);
            return;
        }

        EventItem entity = new EventItem();
        entity.setEvent(event);
        entity.setItem(item);
        entity.setRequestedQuantity(qty);
        entity.setAllocatedQuantity(allocatedQty);
        entity.setUnitPrice(BigDecimal.ZERO);
        entity.setRateType("NONE");
        entity.setLineTotal(BigDecimal.ZERO);
        entity.setStatus(ItemStatus.CONFIRMED);
        entity.setRemark(remark);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setOverbookStatus(OverbookStatus.NONE);
        eventItemRepository.save(entity);
    }

    @Transactional
    public void assignToRoom(Long eventItemId, String roomName, Integer quantity) {
        System.out.println(">>> [ROOM_ASSIGN] ID=" + eventItemId + ", Room=" + roomName + ", Qty=" + quantity);

        EventItem source = eventItemRepository.findById(eventItemId)
                .orElseThrow(() -> new RuntimeException("EventItem not found"));

        int currentQty = nvl(source.getRequestedQuantity());
        if (quantity > currentQty) {
            throw new IllegalArgumentException("Quantity (" + quantity + ") exceeds available (" + currentQty + ")");
        }

        boolean isUnassigning = (roomName == null || roomName.trim().isEmpty()
                || "Unassigned".equalsIgnoreCase(roomName.trim()));

        BigDecimal unitPrice = source.getUnitPrice() != null ? source.getUnitPrice() : BigDecimal.ZERO;

        if (quantity < currentQty) {
            // --- SPLIT PARTIAL QUANTITY ---
            System.out.println("  [SPLIT] Moving partial qty: " + quantity + "/" + currentQty);

            // 1. Calculate how much of 'quantity' is Stock vs Rental
            int currentAlloc = nvl(source.getAllocatedQuantity());
            // STOCK PRIORITY: Consume stock first
            int allocatedToMove = Math.min(quantity, currentAlloc);
            int quantityToMove = quantity;

            // 2. Adjust source
            source.setRequestedQuantity(currentQty - quantityToMove);
            source.setAllocatedQuantity(currentAlloc - allocatedToMove);
            source.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(source.getRequestedQuantity())));
            source.setUpdatedAt(LocalDateTime.now());
            eventItemRepository.save(source);

            // 3. Move the 'quantityToMove' (may need another split if it's mixed)
            processMove(source.getEvent(), source.getItem(), quantityToMove, allocatedToMove, roomName,
                    source.getStatus(), unitPrice, source.getRateType(), "Unassigned from room (split)");

        } else {
            // --- MOVE ENTIRE RECORD ---
            System.out.println("  [MOVE] Moving entire record to: " + (isUnassigning ? "Unassigned" : roomName));

            int currentAlloc = nvl(source.getAllocatedQuantity());

            if (isUnassigning) {
                // Return to unassigned pool (merging handles purely stock or purely rental)
                createInternalEventItem(source.getEvent(), source.getItem(), currentQty, currentAlloc,
                        "Unassigned from room (entire)");
                eventItemRepository.delete(source);
            } else {
                // Moving to a room.
                // CRITICAL: If source is mixed (0 < alloc < req), we MUST split it into pure
                // Stock and pure Rental records.
                if (currentAlloc > 0 && currentAlloc < currentQty) {
                    System.out.println(
                            "  [AUTO-SPLIT] Mixed record detected. Splitting into Stock and Rental before room assignment.");

                    // Create Stock portion in room
                    processMove(source.getEvent(), source.getItem(), currentAlloc, currentAlloc, roomName,
                            source.getStatus(), unitPrice, source.getRateType(), "Stock move");

                    // Create Rental portion in room
                    processMove(source.getEvent(), source.getItem(), currentQty - currentAlloc, 0, roomName,
                            source.getStatus(), unitPrice, source.getRateType(), "Rental move");

                    eventItemRepository.delete(source);
                } else {
                    // Pure record (either all Stock or all Rental)
                    processMove(source.getEvent(), source.getItem(), currentQty, currentAlloc, roomName,
                            source.getStatus(), unitPrice, source.getRateType(), "Pure move");
                    eventItemRepository.delete(source);
                }
            }
        }
    }

    private void processMove(Event event, Item item, int qty, int alloc, String roomName, ItemStatus status,
            BigDecimal unitPrice, String rateType, String remark) {
        boolean isUnassigning = (roomName == null || roomName.trim().isEmpty()
                || "Unassigned".equalsIgnoreCase(roomName.trim()));

        if (isUnassigning) {
            createInternalEventItem(event, item, qty, alloc, remark);
            return;
        }

        String targetRoom = roomName.trim();
        boolean incomingIsRental = (alloc == 0);

        // Find compatible target: Same room AND same allocation status (Stock vs
        // Rental)
        List<EventItem> targetList = eventItemRepository.findByEventIdAndItemId(event.getId(), item.getId());
        EventItem target = targetList.stream()
                .filter(ei -> ei.getMetadata() != null && ei.getMetadata().has("room")
                        && targetRoom.equalsIgnoreCase(ei.getMetadata().get("room").asText()))
                .filter(ei -> {
                    boolean targetIsRental = (nvl(ei.getAllocatedQuantity()) == 0);
                    return incomingIsRental == targetIsRental;
                })
                .findFirst().orElse(null);

        if (target != null) {
            target.setRequestedQuantity(nvl(target.getRequestedQuantity()) + qty);
            target.setAllocatedQuantity(nvl(target.getAllocatedQuantity()) + alloc);
            target.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(target.getRequestedQuantity())));
            target.setUpdatedAt(LocalDateTime.now());
            eventItemRepository.save(target);
        } else {
            EventItem newRecord = new EventItem();
            newRecord.setEvent(event);
            newRecord.setItem(item);
            newRecord.setRequestedQuantity(qty);
            newRecord.setAllocatedQuantity(alloc);
            newRecord.setUnitPrice(unitPrice);
            newRecord.setRateType(rateType != null ? rateType : "fixed");
            newRecord.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(qty)));
            newRecord.setStatus(status);
            newRecord.setCreatedAt(LocalDateTime.now());
            newRecord.setUpdatedAt(LocalDateTime.now());

            var meta = objectMapper.createObjectNode();
            meta.put("room", targetRoom);
            newRecord.setMetadata(meta);

            eventItemRepository.save(newRecord);
        }
    }

    @Transactional
    public void deleteRoomFromEvent(Long eventId, String roomName) {
        // 1. Move all items in this room to Unassigned
        List<EventItem> roomItems = eventItemRepository.findByEventId(eventId).stream()
                .filter(ei -> ei.getMetadata() != null && ei.getMetadata().has("room")
                        && roomName.equalsIgnoreCase(ei.getMetadata().get("room").asText()))
                .toList();

        for (EventItem ei : roomItems) {
            // Unassign each
            assignToRoom(ei.getId(), "Unassigned", nvl(ei.getRequestedQuantity()));
        }

        // 2. Remove room from Event customFields
        eventRepository.findById(eventId).ifPresent(event -> {
            var fields = event.getCustomFields();
            if (fields != null && fields.containsKey("rooms")) {
                Object roomsObj = fields.get("rooms");
                if (roomsObj instanceof List) {
                    List<String> rooms = new ArrayList<>((List<String>) roomsObj);
                    rooms.removeIf(r -> r.equalsIgnoreCase(roomName));
                    fields.put("rooms", rooms);
                    event.setCustomFields(fields);
                    eventRepository.save(event);
                }
            }
        });
    }

    private void handleSplitMerging(Event event, Item item, int reqQty, int allocQty, EventItemRequestDTO req,
            List<EventItemDTO> results, boolean hasSerial, int currentAvailable) {
        boolean incomingIsRental = (allocQty == 0);

        // Find compatible unassigned record for merging
        List<EventItem> existingList = eventItemRepository.findByEventIdAndItemId(event.getId(), item.getId());
        EventItem existing = existingList.stream()
                .filter(ei -> ei.getMetadata() == null || !ei.getMetadata().has("room"))
                .filter(ei -> {
                    boolean existingIsRental = (nvl(ei.getAllocatedQuantity()) == 0);
                    return incomingIsRental == existingIsRental;
                })
                .findFirst().orElse(null);

        if (existing != null) {
            existing.setRequestedQuantity(nvl(existing.getRequestedQuantity()) + reqQty);
            existing.setAllocatedQuantity(nvl(existing.getAllocatedQuantity()) + allocQty);
            existing.setUpdatedAt(LocalDateTime.now());

            if (incomingIsRental) {
                existing.setSource("RENT_EXTERNAL");
                if (existing.getStatus() == ItemStatus.CONFIRMED || existing.getStatus() == ItemStatus.DRAFT
                        || existing.getStatus() == ItemStatus.REQUESTED) {
                    existing.setStatus(ItemStatus.PENDING_RENT);
                }
                existing.setOverbookQty(existing.getRequestedQuantity());
            } else {
                // Update overbook count for stock record if it exceeds available
                int totalRequested = nvl(existing.getRequestedQuantity());
                if (totalRequested > currentAvailable) {
                    existing.setOverbookQty(totalRequested - Math.max(0, currentAvailable));
                    // Optional: Update overbookStatus if needed
                    if (existing.getOverbookStatus() == OverbookStatus.NONE) {
                        existing.setOverbookStatus(OverbookStatus.PENDING);
                    }
                }
            }

            EventItem merged = eventItemRepository.save(existing);
            results.add(toDto(merged));
            return;
        }

        // Create new distinct record
        EventItem entity = new EventItem();
        entity.setEvent(event);
        entity.setItem(item);
        entity.setRequestedQuantity(reqQty);
        entity.setAllocatedQuantity(allocQty);
        entity.setUnitPrice(req.getUnitPrice() != null ? req.getUnitPrice() : BigDecimal.ZERO);
        entity.setRateType(req.getRateType() != null ? req.getRateType() : "NONE");
        entity.setLineTotal(entity.getUnitPrice().multiply(BigDecimal.valueOf(reqQty)));
        entity.setRemark(req.getRemark());
        entity.setStatus(incomingIsRental ? ItemStatus.PENDING_RENT : ItemStatus.CONFIRMED);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        if (incomingIsRental) {
            entity.setSource("RENT_EXTERNAL");
            entity.setOverbookQty(reqQty);
            entity.setOverbookStatus(
                    Boolean.TRUE.equals(req.getAutoApprove()) ? OverbookStatus.APPROVED : OverbookStatus.PENDING);
        } else {
            // Check for overbooking in new stock record
            if (reqQty > currentAvailable) {
                entity.setOverbookQty(reqQty - Math.max(0, currentAvailable));
                entity.setOverbookStatus(OverbookStatus.PENDING);
            } else {
                entity.setOverbookQty(0);
                entity.setOverbookStatus(OverbookStatus.NONE);
            }
        }

        if (hasSerial && req.getSerials() != null) {
            entity.setSerials(objectMapper.valueToTree(req.getSerials()));
        }

        EventItem saved = eventItemRepository.save(entity);
        results.add(toDto(saved));
    }
}
