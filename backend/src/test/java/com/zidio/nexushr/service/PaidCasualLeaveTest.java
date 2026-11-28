package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.LeaveStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaidCasualLeaveTest {
    private final LocalDate start = LocalDate.of(2026, 9, 1);
    private final LocalDate end = LocalDate.of(2026, 9, 30);
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 1, 0, 0);

    private LeaveRequest leave(int day, String type, LeaveStatus status, boolean half) {
        LeaveRequest leave = new LeaveRequest();
        leave.setStartDate(start.withDayOfMonth(day));
        leave.setEndDate(start.withDayOfMonth(day));
        leave.setLeaveType(type);
        leave.setStatus(status);
        leave.setHalfDay(half);
        return leave;
    }

    @Test
    void twoHalfDaysUseOneDayAndOtherTypesAreNotPaid() {
        List<LeaveRequest> leaves = List.of(
                leave(1, "CASUAL", LeaveStatus.PENDING, false),
                leave(2, "EARNED", LeaveStatus.APPROVED, false),
                leave(3, "CASUAL", LeaveStatus.APPROVED, true),
                leave(4, "CASUAL", LeaveStatus.APPROVED, true),
                leave(5, "CASUAL", LeaveStatus.APPROVED, false));
        assertThat(PaidCasualLeave.creditedMinutes(leaves, List.of(), start, end, false, Set.of(), now))
                .isEqualTo(480);
    }

    @Test
    void avoidsDoublePayAndExcludesHolidaysAndFutureDates() {
        AttendanceRecord attendance = new AttendanceRecord();
        attendance.setAttendanceDate(start.withDayOfMonth(2));
        attendance.setCheckInTime(start.withDayOfMonth(2).atTime(9, 0));
        attendance.setCheckOutTime(start.withDayOfMonth(2).atTime(15, 0));
        attendance.setWorkMinutes(360);
        List<LeaveRequest> leaves = List.of(
                leave(1, "CASUAL", LeaveStatus.APPROVED, false),
                leave(2, "CASUAL", LeaveStatus.APPROVED, false),
                leave(3, "CASUAL", LeaveStatus.APPROVED, false));
        PaidCasualLeave.Credit credit = PaidCasualLeave.calculate(leaves, List.of(attendance), start, end,
                false, Set.of(start), start.withDayOfMonth(2).atTime(23, 0));
        assertThat(credit.approvedMinutes()).isEqualTo(480);
        assertThat(credit.creditedMinutes()).isEqualTo(120);
    }
}
