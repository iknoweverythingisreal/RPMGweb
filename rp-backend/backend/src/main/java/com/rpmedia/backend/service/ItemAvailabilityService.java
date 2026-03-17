package com.rpmedia.backend.service;

import com.rpmedia.backend.dto.ItemQuantityAvailabilityDTO;
import com.rpmedia.backend.dto.UsageInfoDTO;
import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.repository.EventItemRepository;
import com.rpmedia.backend.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

import com.rpmedia.backend.model.EventItem;
import java.util.stream.Collectors;
import java.util.List;

@Service
public class ItemAvailabilityService {

        @Autowired
        private ItemRepository itemRepository;

        @Autowired
        private EventItemRepository eventItemRepository;

        public ItemQuantityAvailabilityDTO getQtyAvailability(
                        Long itemId, Long eventId,
                        LocalDate start, LocalDate end) {

                // 1) total qty
                Item item = itemRepository.findById(itemId)
                                .orElseThrow(() -> new RuntimeException("Item not found"));

                int total = item.getTotalQuantity() != null
                                ? item.getTotalQuantity().intValue()
                                : 0;

                // 2) allocated qty in overlapping events
                Long allocated = eventItemRepository.sumAllocatedOverlap(
                                itemId, eventId, start, end);

                int allocatedInt = allocated != null ? allocated.intValue() : 0;

                // 3) detailed usage (conflicts)
                List<EventItem> usage = eventItemRepository.findOverlappingUsage(itemId, start, end);
                List<UsageInfoDTO> usageDetails = usage.stream().map(ei -> {
                        return UsageInfoDTO.builder()
                                        .eventId(ei.getEvent().getId())
                                        .eventName(ei.getEvent().getTitle())
                                        .quantity(ei.getRequestedQuantity() != null ? ei.getRequestedQuantity() : 0)
                                        .startDate(ei.getEvent().getStartDate().toString())
                                        .endDate(ei.getEvent().getEndDate().toString())
                                        .status(ei.getStatus() != null ? ei.getStatus().toString() : "PENDING")
                                        .build();
                }).collect(Collectors.toList());

                // 4) available
                int available = total - allocatedInt;

                ItemQuantityAvailabilityDTO dto = new ItemQuantityAvailabilityDTO();
                dto.setTotal(total);
                dto.setAllocated(allocatedInt);
                dto.setAvailable(Math.max(available, 0));
                dto.setShortage(Math.max(-available, 0));
                dto.setUsageDetails(usageDetails);

                return dto;
        }

        public java.util.Map<Long, Integer> getAllocatedQuantitiesBulk(
                        Long excludeEventId, LocalDate start, LocalDate end) {
                List<Object[]> results = eventItemRepository.sumAllocatedOverlapBulk(excludeEventId, start, end);
                java.util.Map<Long, Integer> map = new java.util.HashMap<>();
                for (Object[] row : results) {
                        Long itemId = (Long) row[0];
                        Long allocated = (Long) row[1];
                        map.put(itemId, allocated != null ? allocated.intValue() : 0);
                }
                return map;
        }
}
