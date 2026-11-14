package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.security.AuthorizationService;
import com.zidio.nexushr.service.AttendanceService;
import com.zidio.nexushr.web.dto.AttendanceImportResponse;
import com.zidio.nexushr.web.dto.DailyCheckInCount;
import com.zidio.nexushr.web.dto.AttendanceMetricsResponse;
import com.zidio.nexushr.web.dto.AttendanceMonthlySummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AuthorizationService authorizationService;

    public AttendanceController(
            AttendanceService attendanceService,
            AuthorizationService authorizationService) {
        this.attendanceService = attendanceService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    public AttendanceRecord create(
            @RequestBody AttendanceRecord attendanceRecord) {

        return attendanceService.create(attendanceRecord);
    }

    @PostMapping("/check-in")
    @PreAuthorize("isAuthenticated()")
    public AttendanceRecord checkIn(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double accuracyMeters,
            Authentication authentication,
            HttpServletRequest request) {

        requireWindowsComputer(request);
        Long actorId = attendanceService.findEmployeeIdByUsername(authentication.getName());
        Long targetEmployeeId = punchTarget(employeeId, actorId, authentication);
        return attendanceService.checkIn(targetEmployeeId, actorId, false, latitude, longitude, accuracyMeters);
    }

    @PostMapping("/check-out")
    @PreAuthorize("isAuthenticated()")
    public AttendanceRecord checkOut(
            @RequestParam(required = false) Long employeeId,
            Authentication authentication,
            HttpServletRequest request) {

        requireWindowsComputer(request);
        Long actorId = attendanceService.findEmployeeIdByUsername(authentication.getName());
        Long targetEmployeeId = punchTarget(employeeId, actorId, authentication);
        return attendanceService.checkOut(targetEmployeeId, actorId);
    }

    @PostMapping("/biometric/check-in")
    @PreAuthorize("isAuthenticated()")
    public AttendanceRecord biometricCheckIn(
            @RequestParam(required = false) Long employeeId,
            Authentication authentication,
            HttpServletRequest request) {

        requireWindowsComputer(request);
        Long selfId = attendanceService.findEmployeeIdByUsername(authentication.getName());
        return attendanceService.biometricCheckIn(punchTarget(employeeId, selfId, authentication));
    }

    @PostMapping("/biometric/check-out")
    @PreAuthorize("isAuthenticated()")
    public AttendanceRecord biometricCheckOut(
            @RequestParam(required = false) Long employeeId,
            Authentication authentication,
            HttpServletRequest request) {

        requireWindowsComputer(request);
        Long selfId = attendanceService.findEmployeeIdByUsername(authentication.getName());
        return attendanceService.biometricCheckOut(punchTarget(employeeId, selfId, authentication));
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
        return attendanceService.auditTrail(employeeId).stream()
                .filter(log -> authorizationService.canSeeEmployeeId(log.getEmployeeId(), authentication))
                .toList();
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
            @RequestParam LocalDate date,
            Authentication authentication) {

        return attendanceService.listByDate(date).stream()
                .filter(record -> authorizationService.canSeeEmployee(record.getEmployee(), authentication))
                .toList();
    }

    @GetMapping("/daily-counts")
    @PreAuthorize("isAuthenticated()")
    public List<DailyCheckInCount> dailyCounts(
            @RequestParam(required = false) String company,
            Authentication authentication) {
        return attendanceService.dailyCheckInCounts(authentication.getName(), company);
    }

    @GetMapping("/range")
    @PreAuthorize("hasAnyRole('HR','ADMIN','MANAGER')")
    public List<AttendanceRecord> listBetween(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            Authentication authentication) {

        return attendanceService.listBetween(from, to).stream()
                .filter(record -> authorizationService.canSeeEmployee(record.getEmployee(), authentication))
                .toList();
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

    private Long punchTarget(Long requestedId, Long selfId, Authentication authentication) {
        if (!isManagement(authentication)) {
            return selfId;
        }
        return requestedId != null ? requestedId : selfId;
    }

    private boolean isManagement(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_HR".equals(role)
                    || "ROLE_ADMIN".equals(role)
                    || "ROLE_MANAGER".equals(role)
                    || "ROLE_SUPER_ADMIN".equals(role)) {
                return true;
            }
        }
        return false;
    }

    private void requireWindowsComputer(HttpServletRequest request) {
        if (!WindowsAttendanceClient.allowed(request.getHeader("User-Agent"))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Check-in and check-out work on a Windows or Mac computer. A phone cannot punch.");
        }
    }
}
