package com.rpmedia.backend.controller;

import com.rpmedia.backend.dto.BulkAvailabilityRequestDTO;
import com.rpmedia.backend.dto.ItemQuantityAvailabilityDTO;
import com.rpmedia.backend.dto.UnifiedAvailabilityDTO;
import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.rpmedia.backend.service.ItemAvailabilityService;
import com.rpmedia.backend.service.ItemService;
import com.rpmedia.backend.service.UnifiedAvailabilityService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/items")
@CrossOrigin(origins = "*")
public class ItemController {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemAvailabilityService itemAvailabilityService;

    @Autowired
    private UnifiedAvailabilityService unifiedAvailabilityService;

    // Get all items
    @GetMapping
    public List<Item> getAllItems() {
        return itemRepository.findAll();
    }

    // Search items by name
    @GetMapping("/search")
    public List<Item> searchItems(@RequestParam("q") String query) {
        return itemRepository.findByNameContainingIgnoreCase(query);
    }

    // Get items by category
    @GetMapping("/category/{category}")
    public List<Item> getItemsByCategory(@PathVariable("category") String category) {
        return itemRepository.findByCategoryIgnoreCase(category);
    }

    // Get item by ID
    @GetMapping("/{id}")
    public Item getItemById(@PathVariable("id") Long id) {
        return itemRepository.findById(id).orElse(null);
    }

    // Create new item
    @PostMapping
    public Item createItem(@RequestBody Item item) {
        return itemService.createItem(item);
    }

    // Update item
    @PutMapping("/{id}")
    public Item updateItem(@PathVariable("id") Long id, @RequestBody Item updatedItem) {
        return itemService.updateItem(id, updatedItem);
    }

    // Delete item
    @DeleteMapping("/{id}")
    public void deleteItem(@PathVariable("id") Long id) {
        itemRepository.deleteById(id);
    }

    @GetMapping("/{itemId}/availability")
    public ResponseEntity<?> getAvailability(
            @PathVariable("itemId") Long itemId,
            @RequestParam("eventId") Long eventId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        ItemQuantityAvailabilityDTO result = itemAvailabilityService.getQtyAvailability(itemId, eventId, start, end);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{itemId}/availability/full")
    public ResponseEntity<?> getUnifiedAvailability(
            @PathVariable("itemId") Long itemId,
            @RequestParam("eventId") Long eventId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        UnifiedAvailabilityDTO result = unifiedAvailabilityService.compute(itemId, eventId, start, end);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/availability/bulk")
    public ResponseEntity<?> getBulkAvailability(@RequestBody BulkAvailabilityRequestDTO request) {
        LocalDate start = LocalDate.parse(request.getStartDate());
        LocalDate end = LocalDate.parse(request.getEndDate());

        List<UnifiedAvailabilityDTO> results = unifiedAvailabilityService.computeBulk(
                request.getItemIds(),
                request.getEventId(),
                start,
                end);

        return ResponseEntity.ok(results);
    }

}
