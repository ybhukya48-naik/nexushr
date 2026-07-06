package com.zidio.nexushr.web;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.service.AttendanceService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping
    public AttendanceRecord create(@RequestBody AttendanceRecord attendanceRecord) {
        return attendanceService.create(attendanceRecord);
    }

    @GetMapping
    public List<AttendanceRecord> listByDate(@RequestParam LocalDate date) {
        return attendanceService.listByDate(date);
    }
}
