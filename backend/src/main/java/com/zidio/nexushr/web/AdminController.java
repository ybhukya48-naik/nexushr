package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.service.AttendanceService;
import com.zidio.nexushr.service.EmployeeService;
import com.zidio.nexushr.security.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    public AdminController(
            EmployeeService employeeService,
            AttendanceService attendanceService,
            AuthorizationService authorizationService) {
        this.employeeService = employeeService;
        this.attendanceService = attendanceService;
        this.authorizationService = authorizationService;
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
}
