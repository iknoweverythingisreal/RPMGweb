package com.rpmedia.backend.service;

import com.rpmedia.backend.model.IntegrationState;
import com.rpmedia.backend.repository.IntegrationStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class IntegrationStateService {

    private final IntegrationStateRepository repository;

    public LocalDateTime getLastSync(String key) {
        return repository.findByKey(key)
                .map(state -> LocalDateTime.parse(state.getValue()))
                .orElse(LocalDateTime.now().minusDays(1)); // ถ้าไม่มีค่า ให้เริ่มย้อนหลัง 1 วัน
    }

    public void updateSyncTime(String key, LocalDateTime time) {
        IntegrationState state = repository.findByKey(key).orElse(new IntegrationState());
        state.setKey(key);
        state.setValue(time.toString());
        state.setUpdatedAt(LocalDateTime.now());
        repository.save(state);
    }
}
