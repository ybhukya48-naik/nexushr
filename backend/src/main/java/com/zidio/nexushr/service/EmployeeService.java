package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.web.dto.EmployeeRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Employee> findAll() {
        return employeeRepository.findAll();
    }

    public Employee findById(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Employee not found with id: " + id
                        ));
    }

    public Employee create(EmployeeRequest request) {
        if (request.getPassword() == null
                || request.getPassword().isBlank()) {
            throw new IllegalArgumentException(
                    "Employee password is required"
            );
        }

        if (employeeRepository.existsByEmployeeCode(
                request.getEmployeeCode())) {
            throw new IllegalArgumentException(
                    "Employee code already exists: " + request.getEmployeeCode()
            );
        }

        if (employeeRepository.existsByEmail(
                request.getEmail())) {
            throw new IllegalArgumentException(
                    "Employee email already exists: " + request.getEmail()
            );
        }

        Employee employee = new Employee();

        employee.setEmployeeCode(request.getEmployeeCode());
        employee.setFullName(request.getFullName());
        employee.setEmail(request.getEmail());
        employee.setPhone(request.getPhone());
        employee.setAccountNumber(request.getAccountNumber());
        employee.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        employee.setRoleType(request.getRoleType());
        employee.setDepartment(request.getDepartment());
        employee.setDesignation(request.getDesignation());
        employee.setJoiningDate(request.getJoiningDate());
        employee.setBaseSalary(request.getBaseSalary());

        employee.setActive(true);
        employee.setLifecycleStatus(
                EmployeeLifecycleStatus.ACTIVE
        );

        return employeeRepository.save(employee);
    }

    public Employee update(
            Long id,
            EmployeeRequest request) {

        Employee employee = findById(id);

        if (request.getEmployeeCode() != null
                && !request.getEmployeeCode().isBlank()) {
            employee.setEmployeeCode(
                    request.getEmployeeCode()
            );
        }

        if (request.getFullName() != null
                && !request.getFullName().isBlank()) {
            employee.setFullName(
                    request.getFullName()
            );
        }

        if (request.getEmail() != null
                && !request.getEmail().isBlank()) {
            employee.setEmail(
                    request.getEmail()
            );
        }

        if (request.getPhone() != null) {
            employee.setPhone(
                    request.getPhone()
            );
        }

        if (request.getAccountNumber() != null) {
            employee.setAccountNumber(
                    request.getAccountNumber()
            );
        }

        if (request.getRoleType() != null) {
            employee.setRoleType(
                    request.getRoleType()
            );
        }

        if (request.getDepartment() != null
                && !request.getDepartment().isBlank()) {
            employee.setDepartment(
                    request.getDepartment()
            );
        }

        if (request.getDesignation() != null
                && !request.getDesignation().isBlank()) {
            employee.setDesignation(
                    request.getDesignation()
            );
        }

        if (request.getJoiningDate() != null) {
            employee.setJoiningDate(
                    request.getJoiningDate()
            );
        }

        if (request.getBaseSalary() != null) {
            employee.setBaseSalary(
                    request.getBaseSalary()
            );
        }

        if (request.getActive() != null) {
            employee.setActive(
                    request.getActive()
            );
        }

        if (request.getPassword() != null
                && !request.getPassword().isBlank()) {
            employee.setPassword(
                    passwordEncoder.encode(
                            request.getPassword()
                    )
            );
        }

        return employeeRepository.save(employee);
    }

    public void delete(Long id) {
        Employee employee = findById(id);

        employee.setActive(false);
        employee.setLifecycleStatus(
                EmployeeLifecycleStatus.OFFBOARDED
        );

        employeeRepository.save(employee);
    }
}



