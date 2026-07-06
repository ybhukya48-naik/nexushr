package com.zidio.nexushr.web;

import com.zidio.nexushr.repository.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayrollRepository payrollRepository;

    public DashboardController(
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository,
            LeaveRequestRepository leaveRequestRepository,
            PayrollRepository payrollRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.payrollRepository = payrollRepository;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return Map.of(
                "totalEmployees", employeeRepository.count(),
                "attendanceEvents", attendanceRepository.count(),
                "leaveRequests", leaveRequestRepository.count(),
                "payrollRecords", payrollRepository.count()
        );
    }
}
