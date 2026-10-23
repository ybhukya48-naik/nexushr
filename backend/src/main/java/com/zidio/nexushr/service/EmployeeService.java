package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.Company;
import com.zidio.nexushr.domain.RoleType;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.CompanyRepository;
import com.zidio.nexushr.web.dto.EmployeeRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;    private final PasswordEncoder passwordEncoder;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            CompanyRepository companyRepository,
            PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
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

        String requestedCode = request.getCompanyCode();
        final String companyCode = (requestedCode == null || requestedCode.isBlank())
                ? "CYOND" : requestedCode;

        Company company = companyRepository.findByCodeIgnoreCase(companyCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown company: " + companyCode
                ));
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
        employee.setCompany(company);
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
        if (request.getSiteVisitor() != null) {
            employee.setSiteVisitor(request.getSiteVisitor());
        }

        return employeeRepository.save(employee);
    }

    public Employee update(
            Long id,
            EmployeeRequest request) {

        Employee employee = findById(id);


        if (request.getCompanyCode() != null && !request.getCompanyCode().isBlank()) {
            Company company = companyRepository.findByCodeIgnoreCase(request.getCompanyCode())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown company: " + request.getCompanyCode()
                    ));

            employee.setCompany(company);
        }
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

        if (request.getSiteVisitor() != null) {
            employee.setSiteVisitor(request.getSiteVisitor());
        }

        if (request.getReportingManagerId() != null) {
            employee.setReportingManagerId(request.getReportingManagerId());
        }

        if (request.getPassword() != null
                && !request.getPassword().isBlank()) {
            employee.setPassword(
                    passwordEncoder.encode(
                            request.getPassword()
                    )
            );
        }

        if (request.getCompanyCode() != null && !request.getCompanyCode().isBlank()) {
            Company company = companyRepository.findByCodeIgnoreCase(request.getCompanyCode())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown company: " + request.getCompanyCode()
                    ));
            employee.setCompany(company);
        }

        return employeeRepository.save(employee);
    }

    public Employee setSiteVisitor(Long id, boolean siteVisitor) {
        Employee employee = findById(id);
        employee.setSiteVisitor(siteVisitor);
        return employeeRepository.save(employee);
    }

    public Employee setRole(Long id, RoleType role) {
        Employee employee = findById(id);
        employee.setRoleType(role);
        return employeeRepository.save(employee);
    }

    public Employee setReportingManager(Long id, Long managerId) {
        Employee employee = findById(id);
        if (managerId != null) {
            if (managerId.equals(id)) {
                throw new IllegalArgumentException("An employee cannot report to themselves");
            }
            findById(managerId);
        }
        employee.setReportingManagerId(managerId);
        return employeeRepository.save(employee);
    }

    public Employee setActive(Long id, boolean active) {
        Employee employee = findById(id);
        employee.setActive(active);
        return employeeRepository.save(employee);
    }

    public Employee saveProfilePhoto(Long id, byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Choose a profile picture");
        }
        if (bytes.length > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Profile picture must be 2 MB or smaller");
        }
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (!type.equals("image/jpeg")
                && !type.equals("image/png")
                && !type.equals("image/webp")
                && !type.equals("image/gif")) {
            throw new IllegalArgumentException("Profile picture must be a JPEG, PNG, WEBP, or GIF");
        }
        Employee employee = findById(id);
        employee.setProfilePhoto(bytes);
        employee.setProfilePhotoType(type);
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
