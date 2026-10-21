package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.NotificationChannel;
import com.zidio.nexushr.domain.NotificationType;
import com.zidio.nexushr.domain.RoleType;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Lazy(false)
public class AttendanceInactivityService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceInactivityService.class);

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public AttendanceInactivityService(
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository,
            NotificationRepository notificationRepository,
            NotificationService notificationService) {
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 11 * * MON-SAT", zone = "Asia/Kolkata")
    @Transactional
    public void alertMissingCheckIn() {
        scan(true);
    }

    @Scheduled(cron = "0 30 19 * * MON-SAT", zone = "Asia/Kolkata")
    @Transactional
    public void alertMissingCheckOut() {
        scan(false);
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
            if (HoursPayPolicy.expectedMinutes(today, today, HoursPayPolicy.gorle(companyCode)) == 0) {
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
        notifyManagers(employee, marker, "did not check in");
    }

    private void markMissingCheckOut(Employee employee, LocalDate today) {
        AttendanceRecord record = attendanceRepository
                .findByEmployee_IdAndAttendanceDate(employee.getId(), today)
                .orElse(null);
        if (record == null || record.getCheckInTime() == null || record.getCheckOutTime() != null) {
            return;
        }
        record.setAttendanceStatus("INACTIVE");
        attendanceRepository.save(record);
        String marker = today + " missing check-out";
        notify(
                employee,
                "Check-out missing",
                marker + ". You did not check out, so this day is marked inactive.");
        notifyManagers(employee, marker, "did not check out");
    }

    private void notifyManagers(Employee employee, String marker, String reason) {
        for (Employee manager : employeeRepository.findAll()) {
            if (manager.getId() == null || !manager.isActive()) {
                continue;
            }
            if (manager.getRoleType() != RoleType.HR && manager.getRoleType() != RoleType.ADMIN) {
                continue;
            }
            if (manager.getId().equals(employee.getId())) {
                continue;
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
        }
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
