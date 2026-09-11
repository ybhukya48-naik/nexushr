package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.PayrollRecord;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.service.PayrollService;
import com.zidio.nexushr.web.dto.PayrollRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PayrollController.class)
@Import(SecurityConfig.class)
class PayrollControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PayrollService payrollService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    private Employee testEmployee() {
        Employee employee = new Employee();
        employee.setId(4L);
        employee.setEmployeeCode("E1002");
        employee.setFullName("Sara Khan");
        employee.setEmail("sara@example.com");
        employee.setRoleType(com.zidio.nexushr.domain.RoleType.EMPLOYEE);
        employee.setDepartment("Engineering");
        employee.setDesignation("Software Engineer");
        employee.setJoiningDate(LocalDate.of(2025, 1, 1));
        employee.setBaseSalary(new BigDecimal("110000"));
        employee.setActive(true);
        return employee;
    }

    @Test
    @WithMockUser(roles = "HR")
    void list_returnsPayrollRecords() throws Exception {

        PayrollRecord record = new PayrollRecord();
        record.setId(1L);
        record.setEmployee(testEmployee());
        record.setPayMonth("2026-07");

        when(payrollService.findAll())
                .thenReturn(List.of(record));

        mockMvc.perform(
                get("/api/v1/payroll")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].payMonth")
                .value("2026-07"))
        .andExpect(jsonPath("$[0].employee.employeeCode")
                .value("E1002"))
        .andExpect(jsonPath("$[0].employee.fullName")
                .value("Sara Khan"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void list_returnsEmptyArray_whenNoRecords()
            throws Exception {

        when(payrollService.findAll())
                .thenReturn(List.of());

        mockMvc.perform(
                get("/api/v1/payroll")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "HR")
    void create_acceptsPayrollRequest()
            throws Exception {

        PayrollRecord record = new PayrollRecord();
        record.setId(2L);
        record.setEmployee(testEmployee());
        record.setPayMonth("2026-07");
        record.setGrossSalary(new BigDecimal("110000"));
        record.setTaxAmount(new BigDecimal("11000"));
        record.setOtherDeductions(new BigDecimal("2000"));
        record.setDeductions(new BigDecimal("13000"));
        record.setNetSalary(new BigDecimal("97000"));

        when(payrollService.create(
                any(PayrollRequest.class)
        )).thenReturn(record);

        mockMvc.perform(
                post("/api/v1/payroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": 4,
                                  "payMonth": "2026-07",
                                  "otherDeductions": 2000
                                }
                                """)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(2))
        .andExpect(jsonPath("$.grossSalary").value(110000))
        .andExpect(jsonPath("$.taxAmount").value(11000))
        .andExpect(jsonPath("$.deductions").value(13000))
        .andExpect(jsonPath("$.netSalary").value(97000))
        .andExpect(jsonPath("$.employee.employeeCode").value("E1002"))
        .andExpect(jsonPath("$.employee.fullName").value("Sara Khan"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void payroll_isForbiddenForEmployee()
            throws Exception {

        mockMvc.perform(
                get("/api/v1/payroll")
        )
        .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void payroll_isAllowedForManager()
            throws Exception {

        when(payrollService.findAll())
                .thenReturn(List.of());

        mockMvc.perform(
                get("/api/v1/payroll")
        )
        .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void payroll_isAllowedForAdmin()
            throws Exception {

        when(payrollService.findAll())
                .thenReturn(List.of());

        mockMvc.perform(
                get("/api/v1/payroll")
        )
        .andExpect(status().isOk());
    }
}
