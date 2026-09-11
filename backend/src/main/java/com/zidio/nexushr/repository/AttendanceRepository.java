package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository
        extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByAttendanceDate(LocalDate attendanceDate);

    boolean existsByEmployee_IdAndAttendanceDate(
            Long employeeId,
            LocalDate attendanceDate
    );

    Optional<AttendanceRecord> findByEmployee_IdAndAttendanceDate(
            Long employeeId,
            LocalDate attendanceDate
    );

    List<AttendanceRecord> findByEmployee_IdOrderByAttendanceDateDesc(
            Long employeeId
    );

    long countByAttendanceDate(LocalDate attendanceDate);

    long countByAttendanceDateAndCheckOutTimeIsNotNull(
            LocalDate attendanceDate
    );

    long countByAttendanceDateAndLateArrivalMinutesGreaterThan(
            LocalDate attendanceDate,
            Integer minutes
    );

    long countByAttendanceDateAndOvertimeMinutesGreaterThan(
            LocalDate attendanceDate,
            Integer minutes
    );
}
