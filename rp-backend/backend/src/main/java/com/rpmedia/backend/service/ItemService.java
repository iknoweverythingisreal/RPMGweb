package com.rpmedia.backend.service;

import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.model.ItemUnit;
import com.rpmedia.backend.model.UnitStatus;
import com.rpmedia.backend.repository.ItemRepository;
import com.rpmedia.backend.repository.ItemUnitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ItemService {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemUnitRepository itemUnitRepository;

    public List<Item> getAllItems() {
        return itemRepository.findAll();
    }

    public List<Item> searchItems(String query) {
        return itemRepository.findByNameContainingIgnoreCase(query);
    }

    public Optional<Item> getItemById(Long id) {
        return itemRepository.findById(id);
    }

    @Transactional
    public Item createItem(Item item) {
        // Default values
        if (item.getStatus() == null)
            item.setStatus(UnitStatus.AVAILABLE);
        if (item.getTotalQuantity() == null)
            item.setTotalQuantity(1);
        item.setAvailableQuantity(item.getTotalQuantity());

        Item savedItem = itemRepository.save(item);

        // Auto-create units
        for (int i = 0; i < savedItem.getTotalQuantity(); i++) {
            ItemUnit unit = new ItemUnit();
            unit.setItem(savedItem);
            unit.setStatus(UnitStatus.AVAILABLE);
            unit.setCreatedAt(Instant.now());

            // Set serial for the first unit if provided
            if (i == 0 && item.getSerial() != null && !item.getSerial().isBlank()) {
                unit.setSerial(item.getSerial());
            } else {
                unit.setSerial("SN-" + savedItem.getId() + "-" + (i + 1));
            }

            itemUnitRepository.save(unit);
        }

        return savedItem;
    }

    public Item updateItem(Long id, Item updatedItem) {
        return itemRepository.findById(id).map(item -> {
            item.setName(updatedItem.getName());
            item.setTotalQuantity(updatedItem.getTotalQuantity());
            item.setDescription(updatedItem.getDescription());
            item.setImageUrl(updatedItem.getImageUrl());
            return itemRepository.save(item);
        }).orElseThrow(() -> new RuntimeException("Item not found"));
    }

    public void deleteItem(Long id) {
        itemRepository.deleteById(id);
    }
}
