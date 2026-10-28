package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.repository.AttendanceAuditRepository;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.AttendanceSelfieRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendancePolicyServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private AttendanceAuditRepository auditRepository;

    @Mock
    private AttendanceSelfieRepository selfieRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private AttendancePolicyService attendancePolicyService;

    @Test
    void automaticLogoutClosesTheOpenDayAt11PmAndKeepsTheRecord() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setFullName("Site Staff");

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setAttendanceDate(LocalDate.of(2026, 10, 22));
        record.setCheckInTime(LocalDateTime.of(2026, 10, 22, 9, 30));
        record.setSessionStartedAt(record.getCheckInTime());
        record.setAttendanceStatus("OPEN");
        record.setClosedWorkMinutes(0);
        record.setBreakMinutes(0);

        when(attendanceRepository.findByEmployee_IdAndAttendanceDateBetween(any(), any(), any()))
                .thenReturn(List.of());

        attendancePolicyService.automaticLogout(record);

        assertThat(record.getCheckOutTime()).isEqualTo(LocalDateTime.of(2026, 10, 22, 23, 0));
        assertThat(record.getBlueFlag()).isTrue();
        assertThat(record.getAttendanceStatus()).isEqualTo("CLOSED");
        assertThat(record.getWorkMinutes()).isEqualTo(750);
        verify(auditRepository).save(any());
    }

    @Test
    void checkOutKeepsClockedHoursWhenChromeWasIdle() {
        Employee employee = new Employee();
        employee.setId(4L);
        employee.setFullName("Yakub");

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setAttendanceDate(LocalDate.of(2026, 10, 26));
        record.setCheckInTime(LocalDateTime.of(2026, 10, 26, 11, 30, 44));
        record.setCheckOutTime(LocalDateTime.of(2026, 10, 26, 17, 52, 46));
        record.setClosedWorkMinutes(337);
        record.setWorkMinutes(337);
        record.setBreakMinutes(0);
        record.setSessionIdleMinutes(270);
        record.setLastActivityAt(LocalDateTime.of(2026, 10, 26, 12, 0));

        when(attendanceRepository.findByEmployee_IdAndAttendanceDateBetween(any(), any(), any()))
                .thenReturn(List.of());

        attendancePolicyService.onCheckOut(record, employee);

        assertThat(record.getWorkMinutes()).isEqualTo(337);
        assertThat(record.getClosedWorkMinutes()).isEqualTo(337);
        assertThat(record.getYellowFlag()).isNotEqualTo(true);
    }
}
