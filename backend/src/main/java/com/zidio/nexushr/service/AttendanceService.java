package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.repository.AttendanceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;

    public AttendanceService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public AttendanceRecord create(AttendanceRecord attendanceRecord) {
        return attendanceRepository.save(attendanceRecord);
    }

    public List<AttendanceRecord> listByDate(LocalDate date) {
        return attendanceRepository.findByAttendanceDate(date);
    }
}
