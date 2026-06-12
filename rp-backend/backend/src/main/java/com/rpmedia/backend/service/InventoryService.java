package com.rpmedia.backend.service;

import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.repository.EventItemRepository;
import com.rpmedia.backend.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventoryService {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private EventItemRepository eventItemRepository;

    /** 🔹 Recalculate all item availability between given dates */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> calculateAvailability(LocalDate from, LocalDate to) {
        List<Item> all = itemRepository.findAll();
        Map<Long, BigDecimal> result = new HashMap<>();

        for (Item item : all) {
            Long allocated = eventItemRepository.sumAllocatedOverlap(
                    item.getId(),
                    null,
                    from,
                    to);
            BigDecimal allocatedVal = BigDecimal.valueOf(allocated != null ? allocated : 0);
            BigDecimal total = BigDecimal.valueOf(item.getTotalQuantity() != null ? item.getTotalQuantity() : 0);

            result.put(item.getId(), total.subtract(allocatedVal).max(BigDecimal.ZERO));
        }
        return result;
    }

    /** 🔹 Mark item quantities as synced (for dashboard or background update) */
    @Transactional
    public void syncVirtualStock(LocalDate from, LocalDate to) {
        Map<Long, BigDecimal> availableMap = calculateAvailability(from, to);

        for (Map.Entry<Long, BigDecimal> entry : availableMap.entrySet()) {
            itemRepository.findById(entry.getKey()).ifPresent(item -> {
                item.setAvailableQuantity(entry.getValue() != null ? entry.getValue().intValue() : 0);
                itemRepository.save(item);
            });
        }

        System.out.println("✅ Virtual stock synced successfully for range: " + from + " → " + to);
    }

    /** 🔹 Check if item is available for the given period */
    @Transactional(readOnly = true)
    public boolean isAvailable(Long itemId, LocalDate from, LocalDate to, int requestedQty) {
        Item item = itemRepository.findById(itemId).orElse(null);
        if (item == null)
            return false;

        Long allocated = eventItemRepository.sumAllocatedOverlap(
                itemId,
                null,
                from,
                to);

        int allocatedVal = allocated != null ? allocated.intValue() : 0;
        int total = item.getTotalQuantity() != null ? item.getTotalQuantity() : 0;

        return (total - allocatedVal) >= requestedQty;
    }

}
