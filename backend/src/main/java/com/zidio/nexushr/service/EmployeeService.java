package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.EmployeeLifecycleStatus;
import com.zidio.nexushr.domain.Company;
import com.zidio.nexushr.domain.RoleType;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.CompanyRepository;
import com.zidio.nexushr.web.dto.EmployeeRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

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

        boolean starter = Boolean.TRUE.equals(request.getStarterAccount());
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        if (starter) {
            String domain = CompanyPasswords.emailDomain(companyCode);
            if (!email.endsWith(domain)) {
                throw new IllegalArgumentException(
                        "Use an employee email that ends with " + domain
                );
            }
        }

        Employee employee = new Employee();
        employee.setCompany(company);
        employee.setEmployeeCode(request.getEmployeeCode());
        employee.setFullName(starter && (request.getFullName() == null || request.getFullName().isBlank())
                ? request.getEmployeeCode()
                : request.getFullName());
        employee.setEmail(email.isBlank() ? request.getEmail() : email);
        employee.setPhone(request.getPhone());
        employee.setAccountNumber(request.getAccountNumber());
        String rawPassword = request.getPassword();
        if (starter && (rawPassword == null || rawPassword.isBlank())) {
            rawPassword = CompanyPasswords.initialPassword(companyCode);
        }
        employee.setPassword(
                rawPassword == null || rawPassword.isBlank()
                        ? ExcelPayrollService.UNSET_PASSWORD_HASH
                        : passwordEncoder.encode(rawPassword)
        );
        if (starter) {
            employee.setPasswordChangeRequired(true);
            employee.setProfileCompleted(false);
            if (request.getDepartment() == null || request.getDepartment().isBlank()) {
                employee.setDepartment(request.getDesignation());
            }
            if (request.getJoiningDate() == null) {
                employee.setJoiningDate(LocalDate.now());
            }
            if (request.getBaseSalary() == null) {
                employee.setBaseSalary(BigDecimal.ZERO);
            }
            if (request.getRoleType() == null) {
                employee.setRoleType(RoleType.EMPLOYEE);
            }
        }
        if (request.getRoleType() != null) {
            employee.setRoleType(request.getRoleType());
        }
        if (request.getDepartment() != null && !request.getDepartment().isBlank()) {
            employee.setDepartment(request.getDepartment());
        }
        employee.setDesignation(request.getDesignation());
        if (request.getJoiningDate() != null) {
            employee.setJoiningDate(request.getJoiningDate());
        }
        if (request.getBaseSalary() != null) {
            employee.setBaseSalary(request.getBaseSalary());
        }

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

    public Employee changeOwnPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters");
        }
        Employee employee = findById(id);
        employee.setPassword(passwordEncoder.encode(newPassword));
        employee.setPasswordChangeRequired(false);
        return employeeRepository.save(employee);
    }

    public Employee completeOwnProfile(Long id, String fullName, String phone, String email) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Enter your name");
        }
        Employee employee = findById(id);
        employee.setFullName(fullName.trim());
        if (phone != null && !phone.isBlank()) {
            employee.setPhone(phone.trim());
        }
        if (email != null && !email.isBlank()) {
            String nextEmail = email.trim().toLowerCase();
            if (!nextEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                throw new IllegalArgumentException("Enter a valid email address");
            }
            employeeRepository.findByEmailIgnoreCase(nextEmail).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new IllegalArgumentException("That email is already used");
                }
            });
            employee.setEmail(nextEmail);
        }
        employee.setProfileCompleted(true);
        return employeeRepository.save(employee);
    }

    public String resetToDefaultPassword(Long id) {
        Employee employee = findById(id);
        return applyDefaultPassword(employee);
    }

    public void setPasswordFromLogin(String email, String currentPassword, String newPassword) {
        Employee employee = findAccountForPasswordHelp(email);
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters");
        }
        String companyCode = companyCode(employee);
        if (newPassword.equals(CompanyPasswords.initialPassword(companyCode))) {
            throw new IllegalArgumentException("Choose a password that is not the default password");
        }
        if (!currentPasswordMatches(employee, currentPassword)) {
            throw new IllegalArgumentException(
                    "Current password does not match. Use the default password, or reset it first."
            );
        }
        employee.setPassword(passwordEncoder.encode(newPassword));
        employee.setPasswordChangeRequired(false);
        employeeRepository.save(employee);
    }

    public String resetPasswordFromLogin(String email) {
        return applyDefaultPassword(requireStaffAccount(email));
    }

    public Employee findAccountForPasswordHelp(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Enter your email");
        }
        Employee employee = employeeRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("No account uses this email"));
        if (!employee.isActive()
                || employee.getLifecycleStatus() == EmployeeLifecycleStatus.OFFBOARDED
                || employee.getLifecycleStatus() == EmployeeLifecycleStatus.OFFBOARDING) {
            throw new IllegalArgumentException("This account is not active");
        }
        return employee;
    }

    public String newSignInPassword() {
        SecureRandom random = new SecureRandom();
        char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
        char[] value = new char[10];
        for (int index = 0; index < value.length; index++) {
            value[index] = alphabet[random.nextInt(alphabet.length)];
        }
        return new String(value);
    }

    public void storeEmailedPassword(Long employeeId, String temporary) {
        if (employeeId == null || temporary == null || temporary.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("No account uses this email"));
        employee.setPassword(passwordEncoder.encode(temporary));
        employee.setPasswordChangeRequired(true);
        employeeRepository.save(employee);
    }

    private String applyDefaultPassword(Employee employee) {
        String temporary = CompanyPasswords.initialPassword(companyCode(employee));
        employee.setPassword(passwordEncoder.encode(temporary));
        employee.setPasswordChangeRequired(true);
        employeeRepository.save(employee);
        return temporary;
    }

    private Employee requireStaffAccount(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Enter your email");
        }
        Employee employee = employeeRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("No employee account uses this email"));
        RoleType role = employee.getRoleType();
        if (role == RoleType.ADMIN || role == RoleType.HR || role == RoleType.SUPER_ADMIN) {
            throw new IllegalArgumentException("HR and admin passwords stay on the HR login.");
        }
        if (!employee.isActive()) {
            throw new IllegalArgumentException("This account is not active");
        }
        return employee;
    }

    private boolean currentPasswordMatches(Employee employee, String rawPassword) {
        String stored = employee.getPassword();
        String fallback = CompanyPasswords.initialPassword(companyCode(employee));
        if (stored == null || stored.isBlank() || ExcelPayrollService.UNSET_PASSWORD_HASH.equals(stored)) {
            return rawPassword != null && rawPassword.equals(fallback);
        }
        try {
            return passwordEncoder.matches(rawPassword, stored);
        } catch (RuntimeException ex) {
            return rawPassword != null && rawPassword.equals(fallback);
        }
    }

    private static String companyCode(Employee employee) {
        return employee.getCompany() == null ? "CYOND" : employee.getCompany().getCode();
    }

    public void delete(Long id) {
        Employee employee = findById(id);
        RoleType role = employee.getRoleType();
        if (role == RoleType.HR || role == RoleType.ADMIN || role == RoleType.SUPER_ADMIN) {
            throw new IllegalArgumentException("HR and admin accounts stay. Delete an employee or a manager.");
        }
        entityManager.createNativeQuery(
                "UPDATE employees SET reporting_manager_id = NULL WHERE reporting_manager_id = :id")
                .setParameter("id", id)
                .executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE attendance_audit_logs SET actor_employee_id = NULL WHERE actor_employee_id = :id")
                .setParameter("id", id)
                .executeUpdate();
        for (String table : List.of(
                "attendance_selfies",
                "attendance_audit_logs",
                "attendance_records",
                "leave_requests",
                "payroll_records",
                "performance_goals",
                "performance_reviews",
                "employee_documents",
                "employee_lifecycle_history",
                "notifications")) {
            entityManager.createNativeQuery("DELETE FROM " + table + " WHERE employee_id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
        }
        entityManager.createNativeQuery(
                "DELETE FROM performance_feedback WHERE employee_id = :id OR reviewer_id = :id")
                .setParameter("id", id)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        employeeRepository.deleteById(id);
    }
}
