package com.rpmedia.backend.service;

import com.rpmedia.backend.model.Event;
import com.rpmedia.backend.model.EventHistory;
import com.rpmedia.backend.repository.EventHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class EventHistoryService {

    @Autowired
    private EventHistoryRepository repository;

    // ✅ ของเดิม — ใช้สำหรับ log แบบ generic (key-value)
    public EventHistory logChange(Long eventId, String changeType, Map<String, Object> data) {
        EventHistory history = new EventHistory(eventId, changeType, data);
        return repository.save(history);
    }

    // ✅ ของใหม่ — ใช้โดย EventItemService (รองรับ userId, action, note)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void log(Long eventId, Long userId, String action, String note) {
        logNonTransactional(eventId, userId, action, note);
    }

    public void logNonTransactional(Long eventId, Long userId, String action, String note) {
        System.out.println(">>> [LOG_HISTORY] EventID=" + eventId + ", Action=" + action);
        try {
            EventHistory history = new EventHistory();
            history.setEventId(eventId);
            history.setUserId(userId);
            history.setAction(action);
            history.setNote(note);
            history.setChangedAt(LocalDateTime.now());

            // Final safety check: if repository is not available don't crash
            if (repository != null) {
                repository.save(history);
                System.out.println("🟢 [History] " + action + " | user=" + userId + " | event=" + eventId);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to log event history (data likely missing in DB): " + e.getMessage());
            // e.printStackTrace(); // Optional for deep debugging
        }
    }

    // ✅ สำหรับหน้า UI ดึงประวัติเรียงเวลาใหม่สุดก่อน
    public List<EventHistory> getHistoryByEvent(Long eventId) {
        return repository.findByEventIdOrderByChangedAtDesc(eventId);

    }
}
