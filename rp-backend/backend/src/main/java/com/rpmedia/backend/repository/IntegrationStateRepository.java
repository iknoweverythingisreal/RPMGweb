package com.rpmedia.backend.repository;

import com.rpmedia.backend.model.IntegrationState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntegrationStateRepository extends JpaRepository<IntegrationState, Long> {
    Optional<IntegrationState> findByKey(String key);
}
