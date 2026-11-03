package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.NotificationType;
import com.zidio.nexushr.repository.AttendanceAuditRepository;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.AttendanceSelfieRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import com.zidio.nexushr.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceInactivityServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private AttendanceAuditRepository auditRepository;

    @Mock
    private AttendanceSelfieRepository selfieRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationService notificationService;

    @Test
    void closeExpiredOpenSessions_logsOutALateCheckInAt11PmAndLeavesTodayOpen() {
        Employee employee = new Employee();
        employee.setId(7L);
        employee.setFullName("Bhukya Yakub");

        LocalDate yesterday = LocalDate.now(AttendanceService.COMPANY_ZONE).minusDays(1);
        AttendanceRecord missed = new AttendanceRecord();
        missed.setId(1L);
        missed.setEmployee(employee);
        missed.setAttendanceDate(yesterday);
        missed.setCheckInTime(yesterday.atTime(10, 15, 21));
        missed.setSessionStartedAt(missed.getCheckInTime());
        missed.setAttendanceStatus("OPEN");
        missed.setClosedWorkMinutes(0);
        missed.setBreakMinutes(0);
        missed.setLateWarning(true);

        LocalDate tomorrow = LocalDate.now(AttendanceService.COMPANY_ZONE).plusDays(1);
        AttendanceRecord stillWorking = new AttendanceRecord();
        stillWorking.setId(2L);
        stillWorking.setEmployee(employee);
        stillWorking.setAttendanceDate(tomorrow);
        stillWorking.setCheckInTime(tomorrow.atTime(10, 15));
        stillWorking.setSessionStartedAt(stillWorking.getCheckInTime());
        stillWorking.setAttendanceStatus("OPEN");
        stillWorking.setClosedWorkMinutes(0);
        stillWorking.setBreakMinutes(0);

        when(attendanceRepository.findByCheckInTimeIsNotNullAndCheckOutTimeIsNull())
                .thenReturn(List.of(missed, stillWorking));
        when(attendanceRepository.findByEmployee_IdAndAttendanceDateBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(notificationRepository.existsByEmployeeIdAndNotificationTypeAndMessageContaining(
                any(), any(NotificationType.class), any())).thenReturn(true);

        AttendancePolicyService policy = new AttendancePolicyService(
                attendanceRepository, auditRepository, selfieRepository, employeeRepository);
        AttendanceInactivityService service = new AttendanceInactivityService(
                employeeRepository,
                attendanceRepository,
                notificationRepository,
                notificationService,
                policy);

        service.closeExpiredOpenSessions();

        assertThat(missed.getCheckOutTime()).isEqualTo(yesterday.atTime(23, 0));
        assertThat(missed.getBlueFlag()).isTrue();
        assertThat(missed.getAttendanceStatus()).isEqualTo("CLOSED");
        assertThat(missed.getWorkMinutes()).isEqualTo(
                AttendanceRules.payableWorkMinutes(
                        AttendanceRules.sessionMinutes(yesterday.atTime(10, 15, 21), yesterday.atTime(23, 0)),
                        0));
        assertThat(stillWorking.getCheckOutTime()).isNull();
    }
}
