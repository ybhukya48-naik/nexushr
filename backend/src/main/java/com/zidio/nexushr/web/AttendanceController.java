package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.service.AttendanceService;
import com.zidio.nexushr.web.dto.AttendanceImportResponse;
import com.zidio.nexushr.web.dto.AttendanceMetricsResponse;
import com.zidio.nexushr.web.dto.AttendanceMonthlySummaryResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping
    public AttendanceRecord create(
            @RequestBody AttendanceRecord attendanceRecord) {

        return attendanceService.create(attendanceRecord);
    }

    @PostMapping("/check-in")
    @PreAuthorize("#employeeId == null || @authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)")
    public AttendanceRecord checkIn(
            @RequestParam(required = false) Long employeeId,
            Authentication authentication) {

        Long targetEmployeeId = employeeId != null
                ? employeeId
                : attendanceService.findEmployeeIdByUsername(authentication.getName());

        return attendanceService.checkIn(targetEmployeeId);
    }

    @PostMapping("/check-out")
    @PreAuthorize("#employeeId == null || @authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)")
    public AttendanceRecord checkOut(
            @RequestParam(required = false) Long employeeId,
            Authentication authentication) {

        Long targetEmployeeId = employeeId != null
                ? employeeId
                : attendanceService.findEmployeeIdByUsername(authentication.getName());

        return attendanceService.checkOut(targetEmployeeId);
    }

    @PostMapping("/biometric/check-in")
    @PreAuthorize("#employeeId == null || @authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)")
    public AttendanceRecord biometricCheckIn(
            @RequestParam(required = false) Long employeeId,
            Authentication authentication) {

        Long targetEmployeeId = employeeId != null
                ? employeeId
                : attendanceService.findEmployeeIdByUsername(authentication.getName());

        return attendanceService.biometricCheckIn(targetEmployeeId);
    }

    @PostMapping("/biometric/check-out")
    @PreAuthorize("#employeeId == null || @authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)")
    public AttendanceRecord biometricCheckOut(
            @RequestParam(required = false) Long employeeId,
            Authentication authentication) {

        Long targetEmployeeId = employeeId != null
                ? employeeId
                : attendanceService.findEmployeeIdByUsername(authentication.getName());

        return attendanceService.biometricCheckOut(targetEmployeeId);
    }

    @GetMapping
    public List<AttendanceRecord> listByDate(
            @RequestParam LocalDate date) {

        return attendanceService.listByDate(date);
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public AttendanceMetricsResponse metrics(
            @RequestParam LocalDate date) {

        return attendanceService.getMetrics(date);
    }

    @PostMapping(
            value = "/import-excel",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public AttendanceImportResponse importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam String payMonth) {

        return attendanceService.importMonthlyExcel(file, payMonth);
    }

    @GetMapping("/monthly-summary")
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public AttendanceMonthlySummaryResponse monthlySummary(
            @RequestParam String payMonth) {

        return attendanceService.monthlySummary(payMonth);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize(
            "@authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)"
    )
    public List<AttendanceRecord> listByEmployee(
            @PathVariable Long employeeId) {

        return attendanceService.listByEmployee(employeeId);
    }
}
