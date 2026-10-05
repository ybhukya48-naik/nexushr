package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.domain.PayrollStatus;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.PayrollRepository;
import com.zidio.nexushr.web.dto.PayrollRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock
    private PayrollRepository payrollRepository;

    @Mock
    private EmployeeRepository employeeRepository;

        @Mock
        private AttendanceRepository attendanceRepository;

    @InjectMocks
    private PayrollService payrollService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                payrollService,
                "taxRate",
                new BigDecimal("0.10")
        );

        employee = new Employee();
        employee.setId(4L);
        employee.setEmployeeCode("E1002");
        employee.setFullName("Sara Khan");
        employee.setEmail("sara.khan@zidio.com");
        employee.setBaseSalary(new BigDecimal("110000"));
        employee.setActive(true);
        employee.setLifecycleStatus(EmployeeLifecycleStatus.ACTIVE);
    }

    @Test
    void create_calculatesGrossTaxDeductionsAndNetSalary() {

        PayrollRequest request = new PayrollRequest();
        request.setEmployeeId(4L);
        request.setPayMonth("2026-09");
        request.setOtherDeductions(new BigDecimal("2000"));

        when(employeeRepository.findById(4L))
                .thenReturn(Optional.of(employee));

        when(payrollRepository.existsByEmployee_IdAndPayMonth(
                4L,
                "2026-09"
        )).thenReturn(false);

        when(payrollRepository.save(any(PayrollRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PayrollRecord result = payrollService.create(request);

        assertThat(result.getEmployee()).isSameAs(employee);
        assertThat(result.getPayMonth()).isEqualTo("2026-09");
        assertThat(result.getGrossSalary())
                .isEqualByComparingTo("110000.00");
        assertThat(result.getTaxAmount())
                .isEqualByComparingTo("11000.00");
        assertThat(result.getOtherDeductions())
                .isEqualByComparingTo("2000.00");
        assertThat(result.getDeductions())
                .isEqualByComparingTo("13000.00");
        assertThat(result.getNetSalary())
                .isEqualByComparingTo("97000.00");
        assertThat(result.getStatus())
                .isEqualTo(PayrollStatus.GENERATED);

        verify(payrollRepository).save(any(PayrollRecord.class));
    }

    @Test
    void create_usesZeroOtherDeductions_whenNotProvided() {

        PayrollRequest request = new PayrollRequest();
        request.setEmployeeId(4L);
        request.setPayMonth("2026-09");

        when(employeeRepository.findById(4L))
                .thenReturn(Optional.of(employee));

        when(payrollRepository.existsByEmployee_IdAndPayMonth(
                4L,
                "2026-09"
        )).thenReturn(false);

        when(payrollRepository.save(any(PayrollRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PayrollRecord result = payrollService.create(request);

        assertThat(result.getOtherDeductions())
                .isEqualByComparingTo("0.00");
        assertThat(result.getDeductions())
                .isEqualByComparingTo("11000.00");
        assertThat(result.getNetSalary())
                .isEqualByComparingTo("99000.00");
    }

    @Test
    void create_rejectsMissingEmployeeId() {

        PayrollRequest request = new PayrollRequest();
        request.setPayMonth("2026-09");

        assertThatThrownBy(() -> payrollService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Employee ID is required");

        verifyNoInteractions(employeeRepository, payrollRepository);
    }

    @Test
    void create_rejectsMissingPayMonth() {

        PayrollRequest request = new PayrollRequest();
        request.setEmployeeId(4L);

        assertThatThrownBy(() -> payrollService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Pay month is required");

        verifyNoInteractions(employeeRepository, payrollRepository);
    }

    @Test
    void create_rejectsInvalidPayMonth() {

        PayrollRequest request = new PayrollRequest();
        request.setEmployeeId(4L);
        request.setPayMonth("09-2026");

        assertThatThrownBy(() -> payrollService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("YYYY-MM");

        verifyNoInteractions(employeeRepository, payrollRepository);
    }

    @Test
    void create_rejectsUnknownEmployee() {

        PayrollRequest request = new PayrollRequest();
        request.setEmployeeId(999L);
        request.setPayMonth("2026-09");

        when(employeeRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> payrollService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Employee not found");

        verify(payrollRepository, never())
                .save(any(PayrollRecord.class));
    }

    @Test
    void create_rejectsInactiveEmployee() {

        employee.setActive(false);

        PayrollRequest request = new PayrollRequest();
        request.setEmployeeId(4L);
        request.setPayMonth("2026-09");

        when(employeeRepository.findById(4L))
                .thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> payrollService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active employee");

        verify(payrollRepository, never())
                .save(any(PayrollRecord.class));
    }

    @Test
    void create_rejectsEmployeeNotInActiveLifecycle() {

        employee.setLifecycleStatus(
                EmployeeLifecycleStatus.OFFBOARDING
        );

        PayrollRequest request = new PayrollRequest();
        request.setEmployeeId(4L);
        request.setPayMonth("2026-09");

        when(employeeRepository.findById(4L))
                .thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> payrollService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active employee");

        verify(payrollRepository, never())
                .save(any(PayrollRecord.class));
    }

    @Test
    void create_rejectsNegativeOtherDeductions() {

        PayrollRequest request = new PayrollRequest();
        request.setEmployeeId(4L);
        request.setPayMonth("2026-09");
        request.setOtherDeductions(new BigDecimal("-1"));

        assertThatThrownBy(() -> payrollService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot be negative");

        verifyNoInteractions(employeeRepository, payrollRepository);
    }

    @Test
    void create_rejectsDuplicatePayroll() {

        PayrollRequest request = new PayrollRequest();
        request.setEmployeeId(4L);
        request.setPayMonth("2026-09");

        when(employeeRepository.findById(4L))
                .thenReturn(Optional.of(employee));

        when(payrollRepository.existsByEmployee_IdAndPayMonth(
                4L,
                "2026-09"
        )).thenReturn(true);

        assertThatThrownBy(() -> payrollService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Payroll already exists");

        verify(payrollRepository, never())
                .save(any(PayrollRecord.class));
    }

    @Test
    void findAll_returnsPayrollRecords() {

        PayrollRecord record = new PayrollRecord();
        record.setId(1L);
        record.setEmployee(employee);
        record.setPayMonth("2026-07");

        when(payrollRepository.findAll())
                .thenReturn(List.of(record));

        List<PayrollRecord> result = payrollService.findAll();

        assertThat(result).containsExactly(record);
        verify(payrollRepository).findAll();
    }

    @Test
    void findAll_returnsEmptyList_whenNoRecords() {

        when(payrollRepository.findAll())
                .thenReturn(List.of());

        assertThat(payrollService.findAll()).isEmpty();

        verify(payrollRepository).findAll();
    }
}
