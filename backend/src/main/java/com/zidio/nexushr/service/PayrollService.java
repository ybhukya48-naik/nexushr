package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.repository.PayrollRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PayrollService {

    private final PayrollRepository payrollRepository;

    public PayrollService(PayrollRepository payrollRepository) {
        this.payrollRepository = payrollRepository;
    }

    public PayrollRecord create(PayrollRecord payrollRecord) {
        return payrollRepository.save(payrollRecord);
    }

    public List<PayrollRecord> findAll() {
        return payrollRepository.findAll();
    }
}
