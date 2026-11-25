package com.zidio.nexushr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.security.AuthorizationService;
import com.zidio.nexushr.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmployeeCtcPrivacyTest {
    @Test
    void annualCtcOnlyAppearsInPrivilegedEmployeeResponses() throws Exception {
        Employee employee = new Employee();
        employee.setAnnualCtc(new BigDecimal("1200000"));
        EmployeeService service = mock(EmployeeService.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        EmployeeController controller = new EmployeeController(service, authorization);
        var manager = new UsernamePasswordAuthenticationToken("manager", null, List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));
        var hr = new UsernamePasswordAuthenticationToken("hr", null, List.of(new SimpleGrantedAuthority("ROLE_HR")));
        when(service.findAll()).thenReturn(List.of(employee));
        when(authorization.canSeeEmployee(any(), any())).thenReturn(true);

        assertThat(controller.list(manager).get(0).annualCtc()).isNull();
        assertThat(controller.list(hr).get(0).annualCtc()).isEqualByComparingTo("1200000");
        assertThat(new ObjectMapper().writeValueAsString(employee)).doesNotContain("annualCtc");
    }
}
