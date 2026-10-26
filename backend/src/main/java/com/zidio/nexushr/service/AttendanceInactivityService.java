package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationType;
import com.zidio.nexushr.domain.RoleType;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.CompanyHolidayRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@Lazy(false)
public class AttendanceInactivityService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceInactivityService.class);

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final AttendancePolicyService attendancePolicyService;

    @Autowired(required = false)
    private CompanyHolidayRepository companyHolidayRepository;

    public AttendanceInactivityService(
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository,
            NotificationRepository notificationRepository,
            NotificationService notificationService,
            AttendancePolicyService attendancePolicyService) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.attendancePolicyService = attendancePolicyService;
    }

    @Scheduled(cron = "0 0 11 * * MON-SAT", zone = "Asia/Kolkata")
    @Transactional
    public void alertMissingCheckIn() {
        scan(true);
    }

    @Scheduled(cron = "0 0 23 * * MON-SAT", zone = "Asia/Kolkata")
    @Transactional
    public void alertMissingCheckOut() {
        scan(false);
    }

    @Scheduled(cron = "0 5 14 * * MON-SAT", zone = "Asia/Kolkata")
    @Transactional
    public void alertMissedLunchReturn() {
        LocalDate today = LocalDate.now(AttendanceService.COMPANY_ZONE);
        for (Employee employee : employeeRepository.findAll()) {
            if (employee.getId() == null || !employee.isActive()) {
                continue;
            }
            attendanceRepository.findByEmployee_IdAndAttendanceDate(employee.getId(), today)
                    .ifPresent(record -> {
                        if (missedLunchReturn(record)) {
                            attendancePolicyService.markMissedLunchReturn(record);
                            attendanceRepository.save(record);
                            notify(employee, "Lunch return missed",
                                    today + " lunch return missed. Back by 2:00 PM after the 1:00–1:45 lunch.");
                        }
                    });
        }
    }

    @Scheduled(cron = "0 */5 9-22 * * MON-SAT", zone = "Asia/Kolkata")
    @Transactional
    public void alertChromeInactive() {
        LocalDateTime now = LocalDateTime.now(AttendanceService.COMPANY_ZONE);
        if (AttendanceRules.duringInactivityGrace(now.toLocalTime())) {
            return;
        }
        LocalDate today = now.toLocalDate();
        for (AttendanceRecord record : attendanceRepository.findByAttendanceDate(today)) {
            if (record.getCheckInTime() == null || record.getCheckOutTime() != null) {
                continue;
            }
            if (record.getEmployee() != null && record.getEmployee().isSiteVisitor()) {
                continue;
            }
            if (Boolean.TRUE.equals(record.getYellowFlag()) || record.getLastActivityAt() == null) {
                continue;
            }
            int gap = AttendanceRules.countableInactiveMinutes(record.getLastActivityAt(), now);
            if (gap > AttendanceRules.INACTIVE_LIMIT_MINUTES) {
                attendancePolicyService.markInactiveChrome(record, (int) gap);
                attendanceRepository.save(record);
            }
        }
    }

    void scan(boolean missingCheckIn) {
        LocalDate today = LocalDate.now(AttendanceService.COMPANY_ZONE);
        List<Employee> people = employeeRepository.findAll();
        for (Employee employee : people) {
            if (employee.getId() == null || !employee.isActive()) {
                continue;
            }
            String companyCode = employee.getCompany() == null
                    ? null
                    : employee.getCompany().getCode();
            if (HoursPayPolicy.expectedMinutes(today, today, HoursPayPolicy.gorle(companyCode)) == 0
                    || isCompanyHoliday(today, companyCode)) {
                continue;
            }
            try {
                if (missingCheckIn) {
                    markMissingCheckIn(employee, today);
                } else {
                    markMissingCheckOut(employee, today);
                }
            } catch (RuntimeException ex) {
                log.warn("Attendance inactive alert failed for employee {}", employee.getId(), ex);
            }
        }
    }

    private boolean isCompanyHoliday(LocalDate today, String companyCode) {
        if (companyHolidayRepository == null) {
            return false;
        }
        return companyHolidayRepository.findByHolidayDateBetween(today, today).stream()
                .anyMatch(holiday -> {
                    String code = holiday.getCompanyCode();
                    return code == null || code.isBlank() || code.equalsIgnoreCase(companyCode);
                });
    }

    private void markMissingCheckIn(Employee employee, LocalDate today) {
        AttendanceRecord record = attendanceRepository
                .findByEmployee_IdAndAttendanceDate(employee.getId(), today)
                .orElse(null);
        if (record != null && record.getCheckInTime() != null) {
            return;
        }
        if (record == null) {
            record = new AttendanceRecord();
            record.setEmployee(employee);
            record.setAttendanceDate(today);
            record.setWorkMinutes(0);
            record.setLateArrivalMinutes(0);
            record.setOvertimeMinutes(0);
            record.setClosedWorkMinutes(0);
            record.setBreakMinutes(0);
        }
        record.setAttendanceStatus("INACTIVE");
        attendanceRepository.save(record);
        String marker = today + " missing check-in";
        notify(
                employee,
                "Check-in missing",
                marker + ". You did not check in, so this day is marked inactive.");
        notifyReportingManager(employee, marker, "did not check in");
    }

    private void markMissingCheckOut(Employee employee, LocalDate today) {
        AttendanceRecord record = attendanceRepository
                .findByEmployee_IdAndAttendanceDate(employee.getId(), today)
                .orElse(null);
        if (record == null || record.getCheckInTime() == null || record.getCheckOutTime() != null) {
            return;
        }
        attendancePolicyService.automaticLogout(record);
        attendanceRepository.save(record);
        String marker = today + " automatic logout";
        notify(
                employee,
                "Automatic logout",
                marker + ". You were logged out at 11:00 PM. The attendance record is kept and marked with a blue flag.");
        notifyReportingManager(employee, marker, "was logged out automatically at 11:00 PM");
    }

    private boolean missedLunchReturn(AttendanceRecord record) {
        if (record.getCheckInTime() == null || Boolean.TRUE.equals(record.getRedFlag())) {
            return false;
        }
        LocalTime arrival = record.getCheckInTime().toLocalTime();
        if (!arrival.isBefore(AttendanceRules.LUNCH_START)) {
            return false;
        }
        boolean stillOpen = record.getCheckOutTime() == null;
        boolean resumedAfterLunch = record.getSessionStartedAt() != null
                && !record.getSessionStartedAt().toLocalTime().isBefore(AttendanceRules.LUNCH_END);
        if (stillOpen) {
            return !resumedAfterLunch;
        }
        LocalTime checkout = record.getCheckOutTime().toLocalTime();
        return !checkout.isBefore(AttendanceRules.LUNCH_START)
                && checkout.isBefore(AttendanceRules.LUNCH_RETURN_DEADLINE)
                && record.getSessionStartedAt() == null;
    }

    private void notifyReportingManager(Employee employee, String marker, String reason) {
        Long managerId = employee.getReportingManagerId();
        if (managerId == null || managerId.equals(employee.getId())) {
            return;
        }
        employeeRepository.findById(managerId).ifPresent(manager -> {
            if (manager.getId() == null || !manager.isActive()) {
                return;
            }
            if (manager.getRoleType() == RoleType.HR
                    || manager.getRoleType() == RoleType.ADMIN
                    || manager.getRoleType() == RoleType.SUPER_ADMIN) {
                return;
            }
            notify(
                    manager,
                    "Attendance inactive",
                    employee.getFullName()
                            + " ("
                            + employee.getEmployeeCode()
                            + ") "
                            + reason
                            + ". "
                            + marker
                            + ".");
        });
    }

    private void notify(Employee employee, String title, String message) {
        boolean alreadySent = notificationRepository
                .existsByEmployeeIdAndNotificationTypeAndMessageContaining(
                        employee.getId(),
                        NotificationType.REMINDER,
                        message);
        if (alreadySent) {
            return;
        }
        boolean hasEmail = employee.getEmail() != null && !employee.getEmail().isBlank();
        notificationService.sendNotification(
                employee.getId(),
                title,
                message,
                NotificationType.REMINDER,
                hasEmail ? NotificationChannel.EMAIL : NotificationChannel.IN_APP,
                employee.getEmail(),
                null);
    }
}
