package com.zidio.nexushr.security;

import com.zidio.nexushr.domain.Company;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ManagerAccessTest {
    @Test
    void managerCanSeeOnlySameCompanyDirectReportsAndOwnPayroll() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        PayrollRepository payrolls = mock(PayrollRepository.class);
        AuthorizationService authorization = new AuthorizationService(employees,
                mock(LeaveRequestRepository.class), payrolls, mock(NotificationRepository.class),
                mock(PerformanceGoalRepository.class), mock(PerformanceFeedbackRepository.class));
        Company cyond = new Company();
        cyond.setCode("CYOND");
        Company gorle = new Company();
        gorle.setCode("GORLE");
        Employee manager = employee(1L, cyond, null);
        Employee report = employee(2L, cyond, 1L);
        Employee other = employee(3L, cyond, 9L);
        Employee foreign = employee(4L, gorle, 1L);
        when(employees.findByEmailIgnoreCase("manager@example.com")).thenReturn(Optional.of(manager));
        var auth = new UsernamePasswordAuthenticationToken("manager@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));

        assertThat(authorization.canSeeEmployee(manager, auth)).isTrue();
        assertThat(authorization.canSeeEmployee(report, auth)).isTrue();
        assertThat(authorization.canSeeEmployee(other, auth)).isFalse();
        assertThat(authorization.canSeeEmployee(foreign, auth)).isFalse();
        assertThat(authorization.canViewPayrollEmployee(1L, auth)).isTrue();
        assertThat(authorization.canViewPayrollEmployee(2L, auth)).isFalse();
        PayrollRecord record = new PayrollRecord();
        record.setEmployee(report);
        when(payrolls.findById(9L)).thenReturn(Optional.of(record));
        assertThat(authorization.canAccessPayroll(9L, auth)).isFalse();
    }

    private static Employee employee(Long id, Company company, Long managerId) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setCompany(company);
        employee.setReportingManagerId(managerId);
        return employee;
    }
}
