package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository
        extends JpaRepository<AttendanceRecord, Long> {

    @EntityGraph(attributePaths = {"employee", "employee.company"})
    List<AttendanceRecord> findByAttendanceDate(LocalDate attendanceDate);

    @EntityGraph(attributePaths = {"employee", "employee.company"})
    List<AttendanceRecord> findByAttendanceDateBetween(
            LocalDate startDate,
            LocalDate endDate
    );

    boolean existsByEmployee_IdAndAttendanceDate(
            Long employeeId,
            LocalDate attendanceDate
    );

    Optional<AttendanceRecord> findByEmployee_IdAndAttendanceDate(
            Long employeeId,
            LocalDate attendanceDate
    );

    @EntityGraph(attributePaths = {"employee", "employee.company"})
    List<AttendanceRecord> findByEmployee_IdOrderByAttendanceDateDesc(
            Long employeeId
    );

    @EntityGraph(attributePaths = {"employee", "employee.company"})
    List<AttendanceRecord> findByCheckInTimeIsNotNullAndCheckOutTimeIsNull();

    List<AttendanceRecord> findByEmployee_IdAndAttendanceDateBetween(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate
    );

    @Query("""
            select a.attendanceDate, count(a)
            from AttendanceRecord a
            where a.checkInTime is not null
              and a.employee.roleType = com.zidio.nexushr.domain.RoleType.EMPLOYEE
              and a.employee.company.code = :companyCode
            group by a.attendanceDate
            order by a.attendanceDate desc
            """)
    List<Object[]> countCheckedInByDate(@Param("companyCode") String companyCode);

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
