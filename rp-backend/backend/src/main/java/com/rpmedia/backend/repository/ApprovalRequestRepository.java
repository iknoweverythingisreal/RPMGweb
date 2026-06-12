package com.rpmedia.backend.repository;

import com.rpmedia.backend.model.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByEventId(Long eventId);

    List<ApprovalRequest> findByStatus(String status);

    List<ApprovalRequest> findByItemId(Long itemId);

}
