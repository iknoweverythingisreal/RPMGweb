package com.rpmedia.backend.service;

import com.rpmedia.backend.dto.ItemQuantityAvailabilityDTO;
import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.repository.EventItemRepository;
import com.rpmedia.backend.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ItemAvailabilityService {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private EventItemRepository eventItemRepository;

    public ItemQuantityAvailabilityDTO getQtyAvailability(
            Long itemId, Long eventId,
            LocalDate start, LocalDate end
    ) {

        // 1) total qty
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        int total = item.getTotalQuantity() != null
                ? item.getTotalQuantity().intValue()
                : 0;

        // 2) allocated qty in overlapping events
        Long allocated = eventItemRepository.sumAllocatedOverlap(
                itemId, eventId, start, end
        );

        int allocatedInt = allocated != null ? allocated.intValue() : 0;

        // 3) available
        int available = total - allocatedInt;

        ItemQuantityAvailabilityDTO dto = new ItemQuantityAvailabilityDTO();
        dto.setTotal(total);
        dto.setAllocated(allocatedInt);
        dto.setAvailable(Math.max(available, 0));
        dto.setShortage(Math.max(-available, 0));

        return dto;
    }
}
