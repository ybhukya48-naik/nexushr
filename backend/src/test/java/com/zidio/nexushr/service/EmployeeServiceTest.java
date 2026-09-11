package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.RoleType;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.web.dto.EmployeeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee employee;
    private EmployeeRequest request;

    @BeforeEach
    void setUp() {

        employee = new Employee();
        employee.setId(1L);
        employee.setEmployeeCode("EMP001");
        employee.setFullName("Alice Smith");
        employee.setEmail("alice@example.com");
        employee.setPassword("$2a$10$encodedPassword");

        request = new EmployeeRequest();
        request.setEmployeeCode("EMP001");
        request.setFullName("Alice Smith");
        request.setEmail("alice@example.com");
        request.setPassword("Password@123");
        request.setRoleType(RoleType.EMPLOYEE);
        request.setDepartment("IT");
        request.setDesignation("Software Engineer");
        request.setJoiningDate(LocalDate.of(2026, 1, 1));
        request.setBaseSalary(new BigDecimal("50000"));
        request.setActive(true);
    }

    @Test
    void findAll_returnsList() {

        when(employeeRepository.findAll())
                .thenReturn(List.of(employee));

        List<Employee> result = employeeService.findAll();

        assertThat(result)
                .hasSize(1)
                .containsExactly(employee);

        verify(employeeRepository).findAll();
    }

    @Test
    void findAll_returnsEmptyList_whenNoEmployees() {

        when(employeeRepository.findAll())
                .thenReturn(List.of());

        List<Employee> result = employeeService.findAll();

        assertThat(result).isEmpty();

        verify(employeeRepository).findAll();
    }

    @Test
    void create_savesAndReturnsEmployee() {

        when(employeeRepository.existsByEmployeeCode("EMP001"))
                .thenReturn(false);

        when(employeeRepository.existsByEmail("alice@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("Password@123"))
                .thenReturn("$2a$10$encodedPassword");

        when(employeeRepository.save(any(Employee.class)))
                .thenReturn(employee);

        Employee result = employeeService.create(request);

        assertThat(result).isSameAs(employee);

        verify(employeeRepository)
                .existsByEmployeeCode("EMP001");

        verify(employeeRepository)
                .existsByEmail("alice@example.com");

        verify(passwordEncoder)
                .encode("Password@123");

        verify(employeeRepository)
                .save(any(Employee.class));
    }

    @Test
    void create_throwsException_whenPasswordMissing() {

        request.setPassword(null);

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Employee password is required");

        verify(employeeRepository, never())
                .save(any(Employee.class));
    }

    @Test
    void create_throwsException_whenPasswordBlank() {

        request.setPassword("   ");

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Employee password is required");

        verify(employeeRepository, never())
                .save(any(Employee.class));
    }

    @Test
    void create_throwsException_whenEmployeeCodeAlreadyExists() {

        when(employeeRepository.existsByEmployeeCode("EMP001"))
                .thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Employee code already exists: EMP001");

        verify(employeeRepository)
                .existsByEmployeeCode("EMP001");

        verify(employeeRepository, never())
                .existsByEmail(anyString());

        verify(employeeRepository, never())
                .save(any(Employee.class));
    }

    @Test
    void create_throwsException_whenEmailAlreadyExists() {

        when(employeeRepository.existsByEmployeeCode("EMP001"))
                .thenReturn(false);

        when(employeeRepository.existsByEmail("alice@example.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Employee email already exists: alice@example.com");

        verify(employeeRepository)
                .existsByEmployeeCode("EMP001");

        verify(employeeRepository)
                .existsByEmail("alice@example.com");

        verify(employeeRepository, never())
                .save(any(Employee.class));
    }

    @Test
    void findById_returnsEmployee_whenFound() {

        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        Employee result = employeeService.findById(1L);

        assertThat(result)
                .isSameAs(employee);

        verify(employeeRepository)
                .findById(1L);
    }

    @Test
    void findById_throwsException_whenNotFound() {

        when(employeeRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.findById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Employee not found with id: 99");

        verify(employeeRepository)
                .findById(99L);
    }

    @Test
    void update_updatesEmployee() {

        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(passwordEncoder.encode("NewPassword@123"))
                .thenReturn("$2a$10$newEncodedPassword");

        when(employeeRepository.save(employee))
                .thenReturn(employee);

        EmployeeRequest updateRequest = new EmployeeRequest();

        updateRequest.setFullName("Alice Updated");
        updateRequest.setEmail("alice.updated@example.com");
        updateRequest.setDepartment("Engineering");
        updateRequest.setDesignation("Senior Software Engineer");
        updateRequest.setBaseSalary(new BigDecimal("65000"));
        updateRequest.setPassword("NewPassword@123");
        updateRequest.setActive(true);

        Employee result = employeeService.update(1L, updateRequest);

        assertThat(result)
                .isSameAs(employee);

        assertThat(employee.getFullName())
                .isEqualTo("Alice Updated");

        assertThat(employee.getEmail())
                .isEqualTo("alice.updated@example.com");

        assertThat(employee.getDepartment())
                .isEqualTo("Engineering");

        assertThat(employee.getDesignation())
                .isEqualTo("Senior Software Engineer");

        assertThat(employee.getBaseSalary())
                .isEqualByComparingTo("65000");

        assertThat(employee.getPassword())
                .isEqualTo("$2a$10$newEncodedPassword");

        verify(employeeRepository)
                .findById(1L);

        verify(passwordEncoder)
                .encode("NewPassword@123");

        verify(employeeRepository)
                .save(employee);
    }

    @Test
    void update_doesNotChangePassword_whenPasswordNotProvided() {

        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        when(employeeRepository.save(employee))
                .thenReturn(employee);

        String originalPassword = employee.getPassword();

        EmployeeRequest updateRequest = new EmployeeRequest();
        updateRequest.setFullName("Alice Updated");

        employeeService.update(1L, updateRequest);

        assertThat(employee.getPassword())
                .isEqualTo(originalPassword);

        verify(passwordEncoder, never())
                .encode(anyString());

        verify(employeeRepository)
                .save(employee);
    }

    @Test
    void delete_deletesEmployee() {

        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        employeeService.delete(1L);

        verify(employeeRepository)
                .findById(1L);

        verify(employeeRepository)
                .delete(employee);
    }
}
