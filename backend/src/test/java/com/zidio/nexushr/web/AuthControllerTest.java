
package com.zidio.nexushr.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zidio.nexushr.domain.Company;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.RoleType;
import com.zidio.nexushr.repository.CompanyRepository;
import com.zidio.nexushr.service.ExcelPayrollService;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.security.JwtTokenService;
import com.zidio.nexushr.service.EmployeeService;
import com.zidio.nexushr.service.email.ResendEmailService;
import com.zidio.nexushr.security.SecurityConfig;
import com.zidio.nexushr.web.dto.AuthDtos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private CompanyRepository companyRepository;

    @MockitoBean
    private EmployeeService employeeService;

    @MockitoBean
    private ResendEmailService resendEmailService;

    @Test
    void login_withAdminCredentials_returnsTokenAndAdminRole() throws Exception {

        when(jwtTokenService.generate(anyString(), anyMap()))
                .thenReturn("mock-jwt-token");

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("admin", "any-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("mock-jwt-token"))
                .andExpect(jsonPath("$.role")
                        .value("ADMIN"))
                .andExpect(jsonPath("$.username")
                        .value("admin"));
    }

    @Test
    void login_withHrUsername_returnsHrRole() throws Exception {

        when(jwtTokenService.generate(anyString(), anyMap()))
                .thenReturn("mock-jwt-token");

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("hr", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role")
                        .value("HR"));
    }

    @Test
    void login_withManagerUsername_returnsManagerRole() throws Exception {

        when(jwtTokenService.generate(anyString(), anyMap()))
                .thenReturn("mock-jwt-token");

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("manager", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role")
                        .value("MANAGER"));
    }

    @Test
    void login_withEmployeeDemoUsername_returnsEmployeeRole() throws Exception {

        when(jwtTokenService.generate(anyString(), anyMap()))
                .thenReturn("mock-jwt-token");

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("employee", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role")
                        .value("EMPLOYEE"));
    }

    @Test
    void login_withUnknownUsername_returnsUnauthorized() throws Exception {

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("john", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Username or password might be incorrect."));
    }

    @Test
    void login_withBlankPassword_returnsBadRequest() throws Exception {

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("admin", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withNullPassword_returnsBadRequest() throws Exception {

        AuthDtos.LoginRequest request =
                new AuthDtos.LoginRequest("admin", null);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withEmployeeCodeAndPassword_returnsToken() throws Exception {
        String hash = new BCryptPasswordEncoder().encode("newpassword1");
        when(employeeRepository.findLoginMatches("GG100"))
                .thenReturn(List.<Object[]>of(new Object[]{
                        "staff@gorlegroup.com",
                        hash,
                        "GG100",
                        "EMPLOYEE",
                        true,
                        "ACTIVE",
                        "Staff Name",
                        "GORLE"
                }));
        when(jwtTokenService.generate(anyString(), anyMap()))
                .thenReturn("employee-token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"GG100","password":"newpassword1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("employee-token"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.username").value("Staff Name"));
    }

    @Test
    void register_adminAndManagerSaveTheirOwnEmail() throws Exception {
        Employee admin = new Employee();
        admin.setId(1L);
        admin.setEmployeeCode("GG-AD-001");
        admin.setEmail("b.ganesh@gorlegroup.com");
        admin.setFullName("B. Ganesh");
        admin.setRoleType(RoleType.ADMIN);
        admin.setPassword(ExcelPayrollService.UNSET_PASSWORD_HASH);
        when(employeeRepository.findByEmployeeCodeIgnoreCase("GG-AD-001")).thenReturn(Optional.of(admin));
        when(employeeRepository.findByEmailIgnoreCase("ganesh.own@gmail.com")).thenReturn(Optional.empty());
        when(employeeRepository.save(org.mockito.ArgumentMatchers.any(Employee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"B. Ganesh","email":"ganesh.own@gmail.com","employeeCode":"GG-AD-001","password":"ganeshpass","companyCode":"GORLE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ganesh.own@gmail.com"))
                .andExpect(jsonPath("$.employeeCode").value("GG-AD-001"));

        assertTrue(new BCryptPasswordEncoder().matches("ganeshpass", admin.getPassword()));
        org.junit.jupiter.api.Assertions.assertEquals(RoleType.ADMIN, admin.getRoleType());
        org.junit.jupiter.api.Assertions.assertEquals("ganesh.own@gmail.com", admin.getEmail());

        Employee manager = new Employee();
        manager.setId(2L);
        manager.setEmployeeCode("GG-TB-001");
        manager.setEmail("akdul.aziz.mohd@gorlegroup.com");
        manager.setFullName("Akdul Aziz Mohd");
        manager.setRoleType(RoleType.MANAGER);
        manager.setPassword(ExcelPayrollService.UNSET_PASSWORD_HASH);
        when(employeeRepository.findByEmployeeCodeIgnoreCase("GG-TB-001")).thenReturn(Optional.of(manager));
        when(employeeRepository.findByEmailIgnoreCase("akdul.own@gmail.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Akdul Aziz Mohd","email":"akdul.own@gmail.com","employeeCode":"GG-TB-001","password":"managerpass","companyCode":"GORLE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("akdul.own@gmail.com"))
                .andExpect(jsonPath("$.employeeCode").value("GG-TB-001"));

        org.junit.jupiter.api.Assertions.assertEquals(RoleType.MANAGER, manager.getRoleType());
        org.junit.jupiter.api.Assertions.assertEquals("akdul.own@gmail.com", manager.getEmail());
    }

    @Test
    void createPassword_forUnsetEmployee_storesNewPassword() throws Exception {
        Employee staff = new Employee();
        staff.setId(9L);
        staff.setEmail("staff@gorlegroup.com");
        staff.setRoleType(RoleType.EMPLOYEE);
        staff.setPassword(ExcelPayrollService.UNSET_PASSWORD_HASH);
        when(employeeRepository.findByEmailIgnoreCase("staff@gorlegroup.com"))
                .thenReturn(Optional.of(staff));
        when(employeeRepository.save(org.mockito.ArgumentMatchers.any(Employee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/auth/create-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"staff@gorlegroup.com","password":"newpassword1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Password created. Sign in with this email and the new password."));

        assertTrue(new BCryptPasswordEncoder().matches("newpassword1", staff.getPassword()));
    }

    @Test
    void createPassword_whenPasswordAlreadySet_returnsConflict() throws Exception {
        Employee staff = new Employee();
        staff.setId(9L);
        staff.setEmail("staff@gorlegroup.com");
        staff.setRoleType(RoleType.EMPLOYEE);
        staff.setPassword(new BCryptPasswordEncoder().encode("already-set-pass"));
        when(employeeRepository.findByEmailIgnoreCase("staff@gorlegroup.com"))
                .thenReturn(Optional.of(staff));

        mockMvc.perform(post("/api/v1/auth/create-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"staff@gorlegroup.com","password":"newpassword1"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void createPassword_whenAccountIsNotEmployee_returnsForbidden() throws Exception {
        Employee hr = new Employee();
        hr.setId(2L);
        hr.setEmail("hr@gorlegroup.com");
        hr.setRoleType(RoleType.HR);
        hr.setPassword(ExcelPayrollService.UNSET_PASSWORD_HASH);
        when(employeeRepository.findByEmailIgnoreCase("hr@gorlegroup.com"))
                .thenReturn(Optional.of(hr));

        mockMvc.perform(post("/api/v1/auth/create-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"hr@gorlegroup.com","password":"newpassword1"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_withoutStaffRecord_isRejected() throws Exception {
        when(employeeRepository.findByEmployeeCodeIgnoreCase("CY-01")).thenReturn(Optional.empty());
        when(employeeRepository.findByEmailIgnoreCase("asha@cyond.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Asha","email":"asha@cyond.com","employeeCode":"CY-01","password":"password12","companyCode":"CYOND"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Ask HR to add you first")));
    }

    @Test
    void register_withExistingUnsetStaff_setsPassword() throws Exception {
        Employee existing = new Employee();
        existing.setId(11L);
        existing.setEmployeeCode("CY-01");
        existing.setEmail("asha@cyond.com");
        existing.setPassword(com.zidio.nexushr.service.ExcelPayrollService.UNSET_PASSWORD_HASH);
        when(employeeRepository.findByEmployeeCodeIgnoreCase("CY-01")).thenReturn(Optional.of(existing));
        when(employeeRepository.findByEmailIgnoreCase("asha@cyond.com")).thenReturn(Optional.of(existing));
        when(employeeRepository.save(org.mockito.ArgumentMatchers.any(Employee.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Asha","email":"asha@cyond.com","employeeCode":"CY-01","password":"password12","companyCode":"CYOND"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("asha@cyond.com"))
                .andExpect(jsonPath("$.employeeCode").value("CY-01"));
    }

    @Test
    void createPassword_whenEmailMissing_returnsNotFound() throws Exception {
        when(employeeRepository.findByEmailIgnoreCase("missing@gorlegroup.com"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/create-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@gorlegroup.com","password":"newpassword1"}
                                """))
                .andExpect(status().isNotFound());
    }
}
