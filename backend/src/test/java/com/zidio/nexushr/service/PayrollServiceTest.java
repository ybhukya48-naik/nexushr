package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.repository.PayrollRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock
    private PayrollRepository payrollRepository;

    @InjectMocks
    private PayrollService payrollService;

    private PayrollRecord payrollRecord;

    @BeforeEach
    void setUp() {
        payrollRecord = new PayrollRecord();
        payrollRecord.setId(1L);
        payrollRecord.setPayMonth("2026-07");
        payrollRecord.setGrossSalary(new BigDecimal("50000"));
        payrollRecord.setDeductions(new BigDecimal("5000"));
        payrollRecord.setNetSalary(new BigDecimal("45000"));
    }

    @Test
    void create_savesAndReturnsPayrollRecord() {
        when(payrollRepository.save(payrollRecord)).thenReturn(payrollRecord);

        PayrollRecord result = payrollService.create(payrollRecord);

        assertThat(result).isSameAs(payrollRecord);
        verify(payrollRepository).save(payrollRecord);
    }

    @Test
    void findAll_returnsList() {
        when(payrollRepository.findAll()).thenReturn(List.of(payrollRecord));

        List<PayrollRecord> result = payrollService.findAll();

        assertThat(result).containsExactly(payrollRecord);
        verify(payrollRepository).findAll();
    }

    @Test
    void findAll_returnsEmptyList_whenNoRecords() {
        when(payrollRepository.findAll()).thenReturn(List.of());

        assertThat(payrollService.findAll()).isEmpty();
    }
}
