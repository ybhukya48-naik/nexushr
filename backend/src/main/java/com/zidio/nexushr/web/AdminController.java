package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.CompanyHoliday;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.RoleType;
import com.zidio.nexushr.repository.CompanyHolidayRepository;
import com.zidio.nexushr.service.AttendanceService;
import com.zidio.nexushr.service.EmployeeService;
import com.zidio.nexushr.security.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final EmployeeService employeeService;
    private final AttendanceService attendanceService;
    private final AuthorizationService authorizationService;
    private final CompanyHolidayRepository companyHolidayRepository;

    public AdminController(
            EmployeeService employeeService,
            AttendanceService attendanceService,
            AuthorizationService authorizationService,
            CompanyHolidayRepository companyHolidayRepository) {
        this.employeeService = employeeService;
        this.attendanceService = attendanceService;
        this.authorizationService = authorizationService;
        this.companyHolidayRepository = companyHolidayRepository;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        LocalDate today = LocalDate.now(AttendanceService.COMPANY_ZONE);
        Map<Long, AttendanceRecord> todayRecords = new LinkedHashMap<>();
        for (AttendanceRecord record : attendanceService.listByDate(today)) {
            if (record.getEmployee() != null && record.getEmployee().getId() != null) {
                todayRecords.put(record.getEmployee().getId(), record);
            }
        }

        List<Employee> employees = employeeService.findAll();
        long active = employees.stream().filter(Employee::isActive).count();
        long missingCheckIn = 0;
        long missingCheckOut = 0;
        List<Map<String, Object>> rows = new java.util.ArrayList<>();

        for (Employee employee : employees) {
            AttendanceRecord record = todayRecords.get(employee.getId());
            boolean checkedIn = record != null && record.getCheckInTime() != null;
            boolean checkedOut = record != null && record.getCheckOutTime() != null;
            String attendanceStatus = record == null || record.getAttendanceStatus() == null
                    ? "NONE"
                    : record.getAttendanceStatus();
            if (employee.isActive() && !checkedIn) {
                missingCheckIn++;
            }
            if (employee.isActive() && checkedIn && !checkedOut) {
                missingCheckOut++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", employee.getId());
            row.put("employeeCode", employee.getEmployeeCode());
            row.put("fullName", employee.getFullName());
            row.put("email", employee.getEmail());
            row.put("role", employee.getRoleType() == null ? "EMPLOYEE" : employee.getRoleType().name());
            row.put("company", employee.getCompany() == null ? "" : employee.getCompany().getCode());
            row.put("active", employee.isActive());
            row.put("attendanceStatus", attendanceStatus);
            row.put("checkInTime", record == null ? null : record.getCheckInTime());
            row.put("checkOutTime", record == null ? null : record.getCheckOutTime());
            row.put("siteVisitor", employee.isSiteVisitor());
            row.put("blueFlag", record != null && Boolean.TRUE.equals(record.getBlueFlag()));
            row.put("yellowFlag", record != null && Boolean.TRUE.equals(record.getYellowFlag()));
            row.put("redFlag", record != null && Boolean.TRUE.equals(record.getRedFlag()));
            row.put("reportingManagerId", employee.getReportingManagerId());
            rows.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("employees", employees.size());
        body.put("active", active);
        body.put("inactiveAccounts", employees.size() - active);
        body.put("missingCheckIn", missingCheckIn);
        body.put("missingCheckOut", missingCheckOut);
        body.put("rows", rows);
        return body;
    }

    @PatchMapping("/employees/{id}/active")
    public Map<String, Object> setActive(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body,
            Authentication authentication) {
        if (body == null || !body.containsKey("active")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "active is required");
        }
        Long currentId = authorizationService.authenticatedEmployeeId(authentication);
        if (currentId != null && currentId.equals(id) && !Boolean.TRUE.equals(body.get("active"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own account");
        }
        Employee updated = employeeService.setActive(id, Boolean.TRUE.equals(body.get("active")));
        return Map.of(
                "id", updated.getId(),
                "active", updated.isActive());
    }

    @PatchMapping("/employees/{id}/site-visitor")
    public Map<String, Object> setSiteVisitor(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        if (body == null || !body.containsKey("siteVisitor")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteVisitor is required");
        }
        Employee updated = employeeService.setSiteVisitor(id, Boolean.TRUE.equals(body.get("siteVisitor")));
        return Map.of(
                "id", updated.getId(),
                "siteVisitor", updated.isSiteVisitor());
    }

    @PatchMapping("/employees/{id}/role")
    public Map<String, Object> setRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        if (body == null || body.get("role") == null || body.get("role").isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role is required");
        }
        Long currentId = authorizationService.authenticatedEmployeeId(authentication);
        if (currentId != null && currentId.equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot change your own role");
        }
        RoleType role;
        try {
            role = RoleType.valueOf(body.get("role").trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role");
        }
        Employee updated = employeeService.setRole(id, role);
        return Map.of("id", updated.getId(), "role", updated.getRoleType().name());
    }

    @PatchMapping("/employees/{id}/reporting-manager")
    public Map<String, Object> setReportingManager(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        if (body == null || !body.containsKey("managerId")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "managerId is required");
        }
        try {
            Employee updated = employeeService.setReportingManager(id, body.get("managerId"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", updated.getId());
            result.put("reportingManagerId", updated.getReportingManagerId());
            return result;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/holidays")
    public List<CompanyHoliday> holidays() {
        return companyHolidayRepository.findAll();
    }

    @PostMapping("/holidays")
    public CompanyHoliday addHoliday(@RequestBody Map<String, String> body) {
        if (body == null || body.get("date") == null || body.get("name") == null || body.get("name").isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Holiday date and name are required");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(body.get("date"));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Holiday date must be YYYY-MM-DD");
        }
        String companyCode = body.get("companyCode");
        if (companyCode != null && companyCode.isBlank()) {
            companyCode = null;
        }
        if (companyHolidayRepository.existsForDay(date, companyCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That holiday is already saved");
        }
        CompanyHoliday holiday = new CompanyHoliday();
        holiday.setHolidayDate(date);
        holiday.setName(body.get("name").trim());
        holiday.setCompanyCode(companyCode);
        return companyHolidayRepository.save(holiday);
    }

    @DeleteMapping("/holidays/{id}")
    public Map<String, Object> deleteHoliday(@PathVariable Long id) {
        if (!companyHolidayRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Holiday not found");
        }
        companyHolidayRepository.deleteById(id);
        return Map.of("deleted", true);
    }
}
