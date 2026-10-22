package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.AttendanceSelfie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AttendanceSelfieRepository extends JpaRepository<AttendanceSelfie, Long> {

    Optional<AttendanceSelfie> findByEmployeeIdAndAttendanceDateAndPunchType(
            Long employeeId,
            LocalDate attendanceDate,
            String punchType);
}
