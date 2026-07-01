package com.rpmedia.backend.bootstrap;

import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.model.UnitStatus;
import com.rpmedia.backend.repository.ItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final ItemRepository itemRepository;

    public DataInitializer(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // ⚠️ seed ข้อมูลลงฐาน — ปิดไว้ default กันไปเขียนข้อมูลลงฐานที่แชร์กับ DMS
        // เปิดเฉพาะเมื่อจงใจ ตั้ง env RPMEDIA_RUN_SEED=true
        if (!"true".equalsIgnoreCase(System.getenv("RPMEDIA_RUN_SEED"))) {
            System.out.println("⏭️ [DataInitializer] seed skipped (ตั้ง RPMEDIA_RUN_SEED=true ถ้าต้องการ seed)");
            return;
        }
        seedServiceItems();
    }

    private void seedServiceItems() {
        List<String> categories = Arrays.asList("LED", "LIGHTING", "SOUND", "VISUAL", "IT");

        for (String category : categories) {
            String itemName = "External Rental - " + category;
            if (itemRepository.findByName(itemName).isEmpty()) {
                Item item = new Item();
                item.setName(itemName);
                item.setCategory(category);
                item.setTotalQuantity(999999);
                item.setAvailableQuantity(999999);
                item.setStatus(UnitStatus.AVAILABLE);
                item.setUom("UNIT");
                item.setSerialControl(false);
                item.setDescription("SERVICE_ITEM");
                item.setPrice(BigDecimal.ZERO);
                itemRepository.save(item);
                System.out.println("Seeded Service Item: " + itemName);
            }
        }
    }
}
