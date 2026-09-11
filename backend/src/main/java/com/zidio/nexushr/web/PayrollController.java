package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.service.PayrollService;
import com.zidio.nexushr.web.dto.PayrollRequest;
import com.zidio.nexushr.web.dto.PayrollResponse;
import com.zidio.nexushr.web.dto.PayslipResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payroll")
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @GetMapping
    public List<PayrollResponse> list() {
        return payrollService.findAll()
                .stream()
                .map(PayrollResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public PayrollResponse getById(@PathVariable Long id) {
        return PayrollResponse.from(payrollService.findById(id));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("@authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)")
    public List<PayrollResponse> listByEmployee(@PathVariable Long employeeId) {
        return payrollService.findByEmployee(employeeId)
                .stream()
                .map(PayrollResponse::from)
                .toList();
    }

    @GetMapping("/{id}/payslip")
    @PreAuthorize("@authorizationService.canAccessPayroll(#id, authentication)")
    public PayslipResponse payslip(@PathVariable Long id) {
        return payrollService.getPayslip(id);
    }

    @PostMapping
    public PayrollResponse create(@RequestBody PayrollRequest request) {
        return PayrollResponse.from(payrollService.create(request));
    }

    @PostMapping("/{id}/mark-paid")
    public PayrollResponse markPaid(@PathVariable Long id) {
        return PayrollResponse.from(payrollService.markPaid(id));
    }
}
