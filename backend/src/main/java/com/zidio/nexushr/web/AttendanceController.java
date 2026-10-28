package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.service.AttendanceService;
import com.zidio.nexushr.web.dto.AttendanceImportResponse;
import com.zidio.nexushr.web.dto.AttendanceMetricsResponse;
import com.zidio.nexushr.web.dto.AttendanceMonthlySummaryResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

        Long actorId = attendanceService.findEmployeeIdIfPresent(authentication.getName());
        return attendanceService.checkIn(targetEmployeeId, actorId, false);
    }

    @PostMapping("/check-out")
    @PreAuthorize("#employeeId == null || @authorizationService.isEmployeeSelfOrManagement(#employeeId, authentication)")
    public AttendanceRecord checkOut(
            @RequestParam(required = false) Long employeeId,
            Authentication authentication) {

        Long targetEmployeeId = employeeId != null
                ? employeeId
                : attendanceService.findEmployeeIdByUsername(authentication.getName());

        Long actorId = attendanceService.findEmployeeIdIfPresent(authentication.getName());
        return attendanceService.checkOut(targetEmployeeId, actorId);
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

    @PostMapping(value = "/site-check-in", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttendanceRecord siteCheckIn(
            @RequestParam("selfie") MultipartFile selfie,
            Authentication authentication) throws IOException {
        Long employeeId = attendanceService.findEmployeeIdByUsername(authentication.getName());
        return attendanceService.siteCheckIn(
                employeeId,
                selfie.getContentType(),
                selfie.getBytes());
    }

    @PostMapping(value = "/site-check-out", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttendanceRecord siteCheckOut(
            @RequestParam("selfie") MultipartFile selfie,
            Authentication authentication) throws IOException {
        Long employeeId = attendanceService.findEmployeeIdByUsername(authentication.getName());
        return attendanceService.siteCheckOut(
                employeeId,
                selfie.getContentType(),
                selfie.getBytes());
    }

    @PostMapping("/heartbeat")
    public java.util.Map<String, Object> heartbeat(Authentication authentication) {
        Long employeeId = attendanceService.findEmployeeIdIfPresent(authentication.getName());
        if (employeeId == null) {
            return java.util.Map.of(
                    "status", "NONE",
                    "checkedIn", false,
                    "checkedOut", false,
                    "message", "Check in is missing");
        }
        attendanceService.heartbeat(employeeId);
        return attendanceService.myStatus(employeeId);
    }

    @GetMapping("/audit")
    public java.util.List<com.zidio.nexushr.domain.AttendanceAuditLog> audit(
            @RequestParam(required = false) Long employeeId,
            Authentication authentication) {
        Long self = attendanceService.findEmployeeIdIfPresent(authentication.getName());
        boolean management = authentication.getAuthorities().stream().anyMatch(authority -> {
            String role = authority.getAuthority();
            return role.equals("ROLE_HR")
                    || role.equals("ROLE_ADMIN")
                    || role.equals("ROLE_SUPER_ADMIN")
                    || role.equals("ROLE_MANAGER");
        });
        if (!management) {
            if (self == null) {
                return java.util.List.of();
            }
            employeeId = self;
        }
        return attendanceService.auditTrail(employeeId);
    }

    @GetMapping("/my-status")
    public java.util.Map<String, Object> myStatus(Authentication authentication) {
        try {
            Long employeeId = attendanceService.findEmployeeIdIfPresent(authentication.getName());
            if (employeeId == null) {
                return java.util.Map.of(
                        "status", "NONE",
                        "checkedIn", false,
                        "checkedOut", false,
                        "message", "Check in is missing");
            }
            return attendanceService.myStatus(employeeId);
        } catch (RuntimeException ex) {
            return java.util.Map.of(
                    "status", "NONE",
                    "checkedIn", false,
                    "checkedOut", false,
                    "message", "Check in is missing");
        }
    }

    @GetMapping
    public List<AttendanceRecord> listByDate(
            @RequestParam LocalDate date) {

        return attendanceService.listByDate(date);
    }

    @GetMapping("/range")
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public List<AttendanceRecord> listBetween(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {

        return attendanceService.listBetween(from, to);
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

    @PostMapping("/import-paste")
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public AttendanceImportResponse importPaste(
            @RequestBody java.util.Map<String, String> body) {
        return attendanceService.importPastedAttendance(
                body == null ? null : body.get("payMonth"),
                body == null ? null : body.get("text"));
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
