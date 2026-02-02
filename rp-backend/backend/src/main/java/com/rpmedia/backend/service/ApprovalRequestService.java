package com.rpmedia.backend.service;

import com.rpmedia.backend.model.ApprovalRequest;
import com.rpmedia.backend.repository.ApprovalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalRequestService {

    private final ApprovalRequestRepository approvalRepo;

    public ApprovalRequest create(ApprovalRequest req) {
        req.setStatus("PENDING");
        return approvalRepo.save(req);
    }

    public List<ApprovalRequest> getPending() {
        return approvalRepo.findByStatus("PENDING");
    }

    public List<ApprovalRequest> getByEvent(Long eventId) {
        return approvalRepo.findByEventId(eventId);
    }

    public ApprovalRequest approve(Long id, Long approverId) {
        ApprovalRequest req = approvalRepo.findById(id).orElseThrow();
        req.setStatus("APPROVED");
        req.setApproverId(approverId);
        return approvalRepo.save(req);
    }

    public ApprovalRequest reject(Long id, Long approverId, String note) {
        ApprovalRequest req = approvalRepo.findById(id).orElseThrow();
        req.setStatus("REJECTED");
        req.setApproverId(approverId);
        req.setNote(note);
        return approvalRepo.save(req);
    }
}
