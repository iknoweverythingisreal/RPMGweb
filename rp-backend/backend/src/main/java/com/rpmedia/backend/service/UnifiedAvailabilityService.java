package com.rpmedia.backend.service;

import com.rpmedia.backend.dto.*;
import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class UnifiedAvailabilityService {

        @Autowired
        private ItemRepository itemRepository;

        @Autowired
        private SerialOpsService serialOpsService;

        @Autowired
        private ItemAvailabilityService qtyService;

        public UnifiedAvailabilityDTO compute(
                        Long itemId, Long eventId,
                        LocalDate start, LocalDate end) {

                Item item = itemRepository.findById(itemId)
                                .orElseThrow(() -> new RuntimeException("Item not found"));

                // FIX: ใช้ getSerialControl() (Boolean) แทน isSerialControl()
                boolean isSerial = item.getSerialControl() != null && item.getSerialControl();

                UnifiedAvailabilityDTO dto = new UnifiedAvailabilityDTO();
                dto.setItemId(itemId);
                dto.setItemName(item.getName());
                dto.setUom(item.getUom());
                dto.setSerialMode(isSerial);

                if (isSerial) {
                        // Serial available
                        List<SerialAvailabilityDTO> serials = serialOpsService.getAvailability(
                                        itemId,
                                        eventId,
                                        start.toString(),
                                        end.toString());

                        dto.setSerials(serials);

                        int total = serials.size();
                        int available = (int) serials.stream()
                                        .filter(s -> "AVAILABLE".equals(s.getStatus())
                                                        || "BOOKED_SELF".equals(s.getStatus()))
                                        .count();
                        int inUse = (int) serials.stream()
                                        .filter(s -> "IN_USE".equals(s.getStatus()))
                                        .count();

                        dto.setTotal(total);
                        dto.setAllocated(inUse);
                        dto.setAvailable(available);
                        dto.setShortage(0);

                } else {

                        ItemQuantityAvailabilityDTO qtyDTO = qtyService.getQtyAvailability(itemId, eventId, start, end);

                        dto.setQty(qtyDTO);
                        dto.setTotal(qtyDTO.getTotal());
                        dto.setAllocated(qtyDTO.getAllocated());
                        dto.setAvailable(qtyDTO.getAvailable());
                        dto.setShortage(qtyDTO.getShortage());
                }

                return dto;
        }

        public List<UnifiedAvailabilityDTO> computeBulk(
                        List<Long> itemIds, Long eventId,
                        LocalDate start, LocalDate end) {

                // 1) Batch find all items
                List<Item> items = itemRepository.findAllById(itemIds);

                // 2) Batch find all quantity-based allocations
                java.util.Map<Long, Integer> allocatedMap = qtyService.getAllocatedQuantitiesBulk(eventId, start, end);

                return items.stream()
                                .map(item -> {
                                        boolean isSerial = item.getSerialControl() != null && item.getSerialControl();

                                        // If serial, we still need physical lookup (Harder to batch-optimize well
                                        // without refactoring serialOps)
                                        // But if not serial, we use our fast map!
                                        if (isSerial) {
                                                return compute(item.getId(), eventId, start, end);
                                        }

                                        UnifiedAvailabilityDTO dto = new UnifiedAvailabilityDTO();
                                        dto.setItemId(item.getId());
                                        dto.setItemName(item.getName());
                                        dto.setUom(item.getUom());
                                        dto.setSerialMode(false);

                                        int total = item.getTotalQuantity() != null ? item.getTotalQuantity() : 0;
                                        int allocated = allocatedMap.getOrDefault(item.getId(), 0);
                                        int available = total - allocated;

                                        dto.setTotal(total);
                                        dto.setAllocated(allocated);
                                        dto.setAvailable(Math.max(available, 0));
                                        dto.setShortage(Math.max(-available, 0));

                                        return dto;
                                })
                                .toList();
        }

        /**
         * 🔹 Validate availability for bulk booking.
         * Throws RuntimeException if requested quantity exceeds available quantity.
         */
        public void validateAvailability(Long itemId, LocalDate start, LocalDate end, int requestedQty) {
                // Reuse existing compute logic
                UnifiedAvailabilityDTO availability = compute(itemId, null, start, end);

                if (availability.getAvailable() < requestedQty) {
                        throw new RuntimeException("Item " + itemId + " (" + availability.getItemName()
                                        + ") is not available. Requested: " + requestedQty + ", Available: "
                                        + availability.getAvailable());
                }
        }
}
