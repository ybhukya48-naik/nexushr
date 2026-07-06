package com.zidio.nexushr.repository;

import com.zidio.nexushr.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findByAttendanceDate(LocalDate attendanceDate);
}
