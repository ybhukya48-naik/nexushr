package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.LeaveStatus;
import com.zidio.nexushr.service.LeaveService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leaves")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @GetMapping
    public List<LeaveRequest> list() {
        return leaveService.findAll();
    }

    @PostMapping
    public LeaveRequest create(@RequestBody LeaveRequest request) {
        return leaveService.create(request);
    }

    @PatchMapping("/{id}/status")
    public LeaveRequest updateStatus(@PathVariable Long id, @RequestParam LeaveStatus status) {
        return leaveService.updateStatus(id, status);
    }
}
