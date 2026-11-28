package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.LeaveRequest;
import com.zidio.nexushr.domain.LeaveStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class PaidCasualLeave {
    private PaidCasualLeave() {
    }

    public record Credit(int approvedMinutes, int creditedMinutes) {
    }

    public static int creditedMinutes(
            List<LeaveRequest> leaves, List<AttendanceRecord> attendance,
            LocalDate start, LocalDate end, boolean gorle, Set<LocalDate> holidays, LocalDateTime now) {
        return calculate(leaves, attendance, start, end, gorle, holidays, now).creditedMinutes();
    }

    public static Credit calculate(
            List<LeaveRequest> leaves, List<AttendanceRecord> attendance,
            LocalDate start, LocalDate end, boolean gorle, Set<LocalDate> holidays, LocalDateTime now) {
        Map<LocalDate, Integer> workedByDate = attendance.stream()
                .filter(record -> record.getAttendanceDate() != null)
                .collect(Collectors.groupingBy(AttendanceRecord::getAttendanceDate,
                        Collectors.summingInt(record -> Math.max(0,
                                AttendanceRules.payrollMinutes(record, now)
                                        - (record.getDeductedMinutes() == null ? 0 : record.getDeductedMinutes())))));
        List<LeaveRequest> approved = leaves.stream()
                .filter(leave -> leave.getStatus() == LeaveStatus.APPROVED
                        && "CASUAL".equalsIgnoreCase(leave.getLeaveType())
                        && leave.getStartDate() != null && leave.getEndDate() != null)
                .sorted(Comparator.comparing(LeaveRequest::getStartDate)
                        .thenComparing(LeaveRequest::getEndDate))
                .toList();
        int remaining = AttendanceRules.WORK_MINUTES;
        int credited = 0;
        Set<LocalDate> countedDates = new java.util.HashSet<>();
        for (LeaveRequest leave : approved) {
            LocalDate first = leave.getStartDate().isAfter(start) ? leave.getStartDate() : start;
            LocalDate last = leave.getEndDate().isBefore(end) ? leave.getEndDate() : end;
            for (LocalDate day = first; !day.isAfter(last) && !day.isAfter(now.toLocalDate()) && remaining > 0;
                    day = day.plusDays(1)) {
                if (!AttendanceRules.isWorkingDay(day, gorle) || holidays.contains(day) || !countedDates.add(day)) {
                    continue;
                }
                int allowance = Math.min(remaining,
                        leave.isHalfDay() ? AttendanceRules.HALF_DAY_MINUTES : AttendanceRules.WORK_MINUTES);
                remaining -= allowance;
                credited += Math.min(allowance,
                        Math.max(0, AttendanceRules.WORK_MINUTES - workedByDate.getOrDefault(day, 0)));
            }
        }
        return new Credit(AttendanceRules.WORK_MINUTES - remaining, credited);
    }
}
