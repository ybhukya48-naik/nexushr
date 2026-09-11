package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleHistory;
import com.zidio.nexushr.service.EmployeeLifecycleService;
import com.zidio.nexushr.web.dto.EmployeeLifecycleRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeLifecycleController {

    private final EmployeeLifecycleService lifecycleService;

    public EmployeeLifecycleController(
            EmployeeLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @PostMapping("/{id}/submit-onboarding")
    public Employee submitOnboarding(
            @PathVariable Long id,
            @RequestBody(required = false) EmployeeLifecycleRequest request) {

        return lifecycleService.submitOnboarding(
                id,
                getChangedBy(request),
                getComments(request)
        );
    }

    @PostMapping("/{id}/approve")
    public Employee approveOnboarding(
            @PathVariable Long id,
            @RequestBody(required = false) EmployeeLifecycleRequest request) {

        return lifecycleService.approveOnboarding(
                id,
                getChangedBy(request),
                getComments(request)
        );
    }

    @PostMapping("/{id}/start-offboarding")
    public Employee startOffboarding(
            @PathVariable Long id,
            @RequestBody(required = false) EmployeeLifecycleRequest request) {

        return lifecycleService.startOffboarding(
                id,
                getChangedBy(request),
                getComments(request)
        );
    }

    @PostMapping("/{id}/complete-offboarding")
    public Employee completeOffboarding(
            @PathVariable Long id,
            @RequestBody(required = false) EmployeeLifecycleRequest request) {

        return lifecycleService.completeOffboarding(
                id,
                getChangedBy(request),
                getComments(request)
        );
    }

    @PostMapping("/{id}/promote")
    public Employee promote(
            @PathVariable Long id,
            @RequestBody(required = false) EmployeeLifecycleRequest request) {

        return lifecycleService.promote(
                id,
                getChangedBy(request),
                getComments(request),
                request != null ? request.getDesignation() : null,
                request != null ? request.getBaseSalary() : null
        );
    }

    @PostMapping("/{id}/transfer")
    public Employee transfer(
            @PathVariable Long id,
            @RequestBody(required = false) EmployeeLifecycleRequest request) {

        return lifecycleService.transfer(
                id,
                getChangedBy(request),
                getComments(request),
                request != null ? request.getDepartment() : null
        );
    }

    @PostMapping("/{id}/resign")
    public Employee resign(
            @PathVariable Long id,
            @RequestBody(required = false) EmployeeLifecycleRequest request) {

        return lifecycleService.resign(
                id,
                getChangedBy(request),
                getComments(request)
        );
    }
    @GetMapping("/{id}/lifecycle-history")
    public List<EmployeeLifecycleHistory> getLifecycleHistory(
            @PathVariable Long id) {

        return lifecycleService.getHistory(id);
    }

    private String getChangedBy(EmployeeLifecycleRequest request) {
        return request != null ? request.getChangedBy() : null;
    }

    private String getComments(EmployeeLifecycleRequest request) {
        return request != null ? request.getComments() : null;
    }
}
