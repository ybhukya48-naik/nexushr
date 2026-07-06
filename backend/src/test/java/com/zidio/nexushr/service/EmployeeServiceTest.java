package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setId(1L);
        employee.setFullName("Alice Smith");
        employee.setEmail("alice@example.com");
    }

    @Test
    void findAll_returnsList() {
        when(employeeRepository.findAll()).thenReturn(List.of(employee));

        List<Employee> result = employeeService.findAll();

        assertThat(result).hasSize(1).containsExactly(employee);
        verify(employeeRepository).findAll();
    }

    @Test
    void findAll_returnsEmptyList_whenNoEmployees() {
        when(employeeRepository.findAll()).thenReturn(List.of());

        List<Employee> result = employeeService.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    void create_savesAndReturnsEmployee() {
        when(employeeRepository.save(employee)).thenReturn(employee);

        Employee result = employeeService.create(employee);

        assertThat(result).isSameAs(employee);
        verify(employeeRepository).save(employee);
    }

    @Test
    void findById_returnsEmployee_whenFound() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        Employee result = employeeService.findById(1L);

        assertThat(result).isSameAs(employee);
    }

    @Test
    void findById_throwsException_whenNotFound() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.findById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Employee not found: 99");
    }
}
