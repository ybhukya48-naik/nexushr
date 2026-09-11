package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    private AttendanceRecord record;
    private Employee employee;

    private final LocalDate date =
            LocalDate.of(2026, 7, 1);

    @BeforeEach
    void setUp() {

        employee = new Employee();
        employee.setId(100L);
        employee.setEmail("employee@example.com");
        employee.setEmployeeCode("EMP100");

        record = new AttendanceRecord();
        record.setId(1L);
        record.setEmployee(employee);
        record.setAttendanceDate(date);

        record.setCheckInTime(
                LocalDateTime.of(2026, 7, 1, 9, 0)
        );

        record.setCheckOutTime(
                LocalDateTime.of(2026, 7, 1, 17, 0)
        );

        record.setWorkMinutes(480);
    }

    @Test
    void create_savesAndReturnsRecord() {

        when(employeeRepository.findById(100L))
                .thenReturn(Optional.of(employee));

        when(attendanceRepository
                .existsByEmployee_IdAndAttendanceDate(
                        100L,
                        date))
                .thenReturn(false);

        when(attendanceRepository.save(record))
                .thenReturn(record);

        AttendanceRecord result =
                attendanceService.create(record);

        assertThat(result)
                .isSameAs(record);

        assertThat(result.getWorkMinutes())
                .isEqualTo(480);

        verify(employeeRepository)
                .findById(100L);

        verify(attendanceRepository)
                .existsByEmployee_IdAndAttendanceDate(
                        100L,
                        date);

        verify(attendanceRepository)
                .save(record);
    }

    @Test
    void checkIn_createsTodaysAttendance() {

        when(employeeRepository.findById(100L))
                .thenReturn(Optional.of(employee));

        when(attendanceRepository
                .existsByEmployee_IdAndAttendanceDate(
                        eq(100L),
                        any(LocalDate.class)))
                .thenReturn(false);

        AttendanceRecord savedRecord =
                new AttendanceRecord();

        savedRecord.setEmployee(employee);
        savedRecord.setAttendanceDate(LocalDate.now());
        savedRecord.setWorkMinutes(0);

        when(attendanceRepository.save(
                any(AttendanceRecord.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        AttendanceRecord result =
                attendanceService.checkIn(100L);

        assertThat(result.getEmployee())
                .isSameAs(employee);

        assertThat(result.getAttendanceDate())
                .isEqualTo(LocalDate.now());

        assertThat(result.getCheckInTime())
                .isNotNull();

        assertThat(result.getCheckOutTime())
                .isNull();

        assertThat(result.getWorkMinutes())
                .isEqualTo(0);

        verify(employeeRepository)
                .findById(100L);

        verify(attendanceRepository)
                .existsByEmployee_IdAndAttendanceDate(
                        eq(100L),
                        eq(LocalDate.now()));

        verify(attendanceRepository)
                .save(any(AttendanceRecord.class));
    }

    @Test
    void checkOut_updatesTodaysAttendance() {

        AttendanceRecord todayRecord =
                new AttendanceRecord();

        todayRecord.setId(10L);
        todayRecord.setEmployee(employee);
        todayRecord.setAttendanceDate(LocalDate.now());

        todayRecord.setCheckInTime(
                LocalDateTime.now().minusHours(8)
        );

        todayRecord.setCheckOutTime(null);
        todayRecord.setWorkMinutes(0);

        when(employeeRepository.findById(100L))
                .thenReturn(Optional.of(employee));

        when(attendanceRepository
                .findByEmployee_IdAndAttendanceDate(
                        eq(100L),
                        eq(LocalDate.now())))
                .thenReturn(Optional.of(todayRecord));

        when(attendanceRepository.save(todayRecord))
                .thenReturn(todayRecord);

        AttendanceRecord result =
                attendanceService.checkOut(100L);

        assertThat(result)
                .isSameAs(todayRecord);

        assertThat(result.getCheckOutTime())
                .isNotNull();

        assertThat(result.getWorkMinutes())
                .isGreaterThanOrEqualTo(0);

        verify(employeeRepository)
                .findById(100L);

        verify(attendanceRepository)
                .findByEmployee_IdAndAttendanceDate(
                        100L,
                        LocalDate.now());

        verify(attendanceRepository)
                .save(todayRecord);
    }

    @Test
    void listByEmployee_returnsEmployeeAttendance() {

        when(employeeRepository.findById(100L))
                .thenReturn(Optional.of(employee));

        when(attendanceRepository
                .findByEmployee_IdOrderByAttendanceDateDesc(100L))
                .thenReturn(List.of(record));

        List<AttendanceRecord> result =
                attendanceService.listByEmployee(100L);

        assertThat(result)
                .containsExactly(record);

        verify(employeeRepository)
                .findById(100L);

        verify(attendanceRepository)
                .findByEmployee_IdOrderByAttendanceDateDesc(100L);
    }

    @Test
    void listByDate_returnsRecordsForDate() {

        when(attendanceRepository
                .findByAttendanceDate(date))
                .thenReturn(List.of(record));

        List<AttendanceRecord> result =
                attendanceService.listByDate(date);

        assertThat(result)
                .containsExactly(record);

        verify(attendanceRepository)
                .findByAttendanceDate(date);
    }

    @Test
    void listByDate_returnsEmptyList_whenNoRecordsForDate() {

        LocalDate noRecordsDate =
                LocalDate.of(2025, 1, 1);

        when(attendanceRepository
                .findByAttendanceDate(noRecordsDate))
                .thenReturn(List.of());

        assertThat(
                attendanceService.listByDate(noRecordsDate)
        ).isEmpty();

        verify(attendanceRepository)
                .findByAttendanceDate(noRecordsDate);
    }
}
