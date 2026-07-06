package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.LeaveStatus;
import com.zidio.nexushr.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveService(LeaveRequestRepository leaveRequestRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
    }

    public LeaveRequest create(LeaveRequest request) {
        return leaveRequestRepository.save(request);
    }

    public List<LeaveRequest> findAll() {
        return leaveRequestRepository.findAll();
    }

    public LeaveRequest updateStatus(Long id, LeaveStatus status) {
        LeaveRequest request = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + id));
        request.setStatus(status);
        return leaveRequestRepository.save(request);
    }
}
