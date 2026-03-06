package com.rpmedia.backend.service;

import com.rpmedia.backend.dto.ItemShortageDTO;
import com.rpmedia.backend.model.Event;
import com.rpmedia.backend.model.EventItem;
import com.rpmedia.backend.repository.EventItemRepository;
import com.rpmedia.backend.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ShortageService {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventItemRepository eventItemRepository;

    // --- helpers ---
    private static int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private static int toInt(BigDecimal v) {
        return v == null ? 0 : v.setScale(0, RoundingMode.DOWN).intValue();
    }

    private static boolean overlaps(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        // ช่วงทับซ้อนถ้า A ไม่จบก่อน B เริ่ม และ A ไม่เริ่มหลัง B จบ
        return !aEnd.isBefore(bStart) && !aStart.isAfter(bEnd);
    }

    public List<ItemShortageDTO> getShortagesForEvent(Long eventId) {
        Event current = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        // ของในอีเวนต์นี้
        List<EventItem> currentItems = eventItemRepository.findByEventId(eventId);

        // ของของทุกอีเวนต์ (เอาไว้หาที่ชน) — ถ้าต้องการ performance ค่อยทำเป็น query
        // เฉพาะช่วง
        List<EventItem> allEventItems = eventItemRepository.findAll();

        List<ItemShortageDTO> result = new ArrayList<>();

        for (EventItem ei : currentItems) {
            // Skip items with no physical inventory record (e.g. External Rentals)
            if (ei.getItem() == null)
                continue;

            Long itemId = ei.getItem().getId();
            String itemName = ei.getItem().getName();

            // total เป็น Integer แล้ว
            int total = nvl(ei.getItem().getTotalQuantity());

            // ใช้ requestedQuantity (ไม่ใช้ field เก่า quantity)
            int requested = nvl(ei.getRequestedQuantity());

            // ช่วงของ "อีเวนต์ปัจจุบัน"
            LocalDate curStart = current.getStartDate();
            LocalDate curEnd = current.getEndDate();

            // รวมของที่ "อีเวนต์อื่น" ใช้ item เดียวกัน และช่วงทับซ้อนกับอีเวนต์ปัจจุบัน
            int bookedByOthers = allEventItems.stream()
                    .filter(x -> !x.getEvent().getId().equals(current.getId())) // ไม่ใช่อีเวนต์นี้
                    .filter(x -> x.getItem().getId().equals(itemId)) // item เดียวกัน
                    .mapToInt(x -> {
                        // กำหนดช่วงของอีกงาน: เริ่ม = start_date, จบ = min(returnDate, end_date)
                        LocalDate otherStart = x.getEvent().getStartDate();
                        LocalDate otherEnd = (x.getReturnDate() != null) ? x.getReturnDate()
                                : x.getEvent().getEndDate();
                        boolean o = overlaps(otherStart, otherEnd, curStart, curEnd);
                        return o ? nvl(x.getRequestedQuantity()) : 0;
                    })
                    .sum();

            int available = Math.max(total - bookedByOthers, 0);
            int shortage = Math.max(0, requested - available);

            result.add(new ItemShortageDTO(
                    itemId, itemName, total, requested, bookedByOthers, available, shortage));
        }

        return result;
    }
}
