package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.service.PayrollService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payroll")
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @GetMapping
    public List<PayrollRecord> list() {
        return payrollService.findAll();
    }

    @PostMapping
    public PayrollRecord create(@RequestBody PayrollRecord payrollRecord) {
        return payrollService.create(payrollRecord);
    }
}
