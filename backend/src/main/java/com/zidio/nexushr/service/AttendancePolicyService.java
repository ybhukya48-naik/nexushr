package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.AttendanceAuditLog;
import com.zidio.nexushr.domain.AttendanceRecord;
import com.zidio.nexushr.domain.AttendanceSelfie;
import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.repository.AttendanceAuditRepository;
import com.zidio.nexushr.repository.AttendanceRepository;
import com.zidio.nexushr.repository.AttendanceSelfieRepository;
import com.zidio.nexushr.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class AttendancePolicyService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceAuditRepository auditRepository;
    private final AttendanceSelfieRepository selfieRepository;
    private final EmployeeRepository employeeRepository;

    public AttendancePolicyService(
            AttendanceRepository attendanceRepository,
            AttendanceAuditRepository auditRepository,
            AttendanceSelfieRepository selfieRepository,
            EmployeeRepository employeeRepository) {
        this.attendanceRepository = attendanceRepository;
        this.auditRepository = auditRepository;
        this.selfieRepository = selfieRepository;
        this.employeeRepository = employeeRepository;
    }

    public void onCheckIn(AttendanceRecord record, Employee actor, boolean selfieAttached) {
        LocalDateTime now = record.getCheckInTime() == null
                ? LocalDateTime.now(AttendanceService.COMPANY_ZONE)
                : record.getSessionStartedAt() == null
                ? record.getCheckInTime()
                : record.getSessionStartedAt();
        if (record.getSessionStartedAt() != null) {
            now = record.getSessionStartedAt();
        }
        record.setLastActivityAt(now);
        if (AttendanceRules.isLateArrival(record.getCheckInTime().toLocalTime())
                && !Boolean.TRUE.equals(record.getLateWarning())) {
            record.setLateWarning(true);
            record.setLateArrivalMinutes(
                    AttendanceRules.lateArrivalMinutes(record.getCheckInTime().toLocalTime()));
        }
        applyMonthPenalties(record);
        String detail = selfieAttached
                ? "Site check-in with selfie at " + now
                : "Check-in at " + now;
        audit(record, actor, "CHECK_IN", detail);
    }

    public void onCheckOut(AttendanceRecord record, Employee actor) {
        LocalDateTime now = record.getCheckOutTime();
        int idle = idleMinutes(record, now, !actorIsSiteVisitor(record));
        if (idle > 0) {
            int closed = record.getClosedWorkMinutes() == null ? 0 : record.getClosedWorkMinutes();
            int payable = Math.max(0, closed - idle);
            record.setClosedWorkMinutes(payable);
            record.setWorkMinutes(payable);
        }
        record.setSessionIdleMinutes(0);
        record.setLastActivityAt(now);
        applyMonthPenalties(record);
        audit(record, actor, "CHECK_OUT", "Check-out at " + now + ". Attendance record kept.");
    }

    @Transactional
    public AttendanceRecord heartbeat(Long employeeId) {
        LocalDate today = LocalDate.now(AttendanceService.COMPANY_ZONE);
        LocalDateTime now = LocalDateTime.now(AttendanceService.COMPANY_ZONE);
        AttendanceRecord record = attendanceRepository
                .findByEmployee_IdAndAttendanceDate(employeeId, today)
                .orElse(null);
        if (record == null || record.getCheckInTime() == null || record.getCheckOutTime() != null) {
            return record;
        }
        Employee employee = record.getEmployee();
        if (employee != null && employee.isSiteVisitor()) {
            record.setLastActivityAt(now);
            return attendanceRepository.save(record);
        }
        int gap = AttendanceRules.countableInactiveMinutes(record.getLastActivityAt(), now);
        if (gap > AttendanceRules.INACTIVE_LIMIT_MINUTES
                && !AttendanceRules.duringInactivityGrace(now.toLocalTime())) {
            record.setSessionIdleMinutes(zero(record.getSessionIdleMinutes()) + gap);
            if (!Boolean.TRUE.equals(record.getYellowFlag())) {
                record.setYellowFlag(true);
                applyMonthPenalties(record);
                audit(record, employee, "YELLOW_FLAG",
                        "Chrome was inactive for " + gap + " minutes. Three yellow flags in a month deduct half a day.");
            }
        }
        record.setLastActivityAt(now);
        return attendanceRepository.save(record);
    }

    public void automaticLogout(AttendanceRecord record) {
        if (record.getCheckInTime() == null || record.getCheckOutTime() != null) {
            return;
        }
        LocalDate date = record.getAttendanceDate() == null
                ? LocalDate.now(AttendanceService.COMPANY_ZONE)
                : record.getAttendanceDate();
        LocalDateTime deadline = date.atTime(AttendanceRules.LOGOUT_DEADLINE);
        AttendanceDay day = new AttendanceDay();
        int closed = record.getClosedWorkMinutes() == null ? 0 : record.getClosedWorkMinutes();
        day.restore(
                record.getCheckInTime(),
                record.getCheckOutTime(),
                record.getSessionStartedAt(),
                closed,
                record.getBreakMinutes() == null ? 0 : record.getBreakMinutes(),
                record.getAttendanceStatus());
        try {
            day.checkOut(deadline);
            record.setCheckOutTime(day.lastCheckOut());
            record.setSessionStartedAt(null);
            record.setClosedWorkMinutes(day.closedWorkMinutes());
            record.setBreakMinutes(day.breakMinutes());
            record.setWorkMinutes(day.closedWorkMinutes());
            record.setAttendanceStatus("CLOSED");
        } catch (IllegalStateException ex) {
            record.setAttendanceStatus("BLUE_FLAG");
        }
        record.setBlueFlag(true);
        record.setLastActivityAt(deadline);
        applyMonthPenalties(record);
        audit(record, record.getEmployee(), "AUTO_LOGOUT",
                "Automatic logout at 11:00 PM. The attendance record is kept.");
    }

    public void markMissingLogout(AttendanceRecord record) {
        automaticLogout(record);
    }

    public void markInactiveChrome(AttendanceRecord record, int gap) {
        if (Boolean.TRUE.equals(record.getYellowFlag())) {
            return;
        }
        record.setYellowFlag(true);
        record.setSessionIdleMinutes(zero(record.getSessionIdleMinutes()) + gap);
        applyMonthPenalties(record);
        audit(record, record.getEmployee(), "YELLOW_FLAG",
                "System inactive for " + gap + " minutes. Three yellow flags in a month deduct half a day.");
    }

    public void markMissedLunchReturn(AttendanceRecord record) {
        if (Boolean.TRUE.equals(record.getRedFlag()) || record.getCheckInTime() == null) {
            return;
        }
        record.setRedFlag(true);
        applyMonthPenalties(record);
        audit(record, record.getEmployee(), "RED_FLAG",
                "Did not resume within 15 minutes after the 1:00–1:45 lunch. Three red flags deduct half a day.");
    }

    @Transactional
    public AttendanceRecord saveSelfie(
            Long employeeId,
            LocalDate date,
            String contentType,
            byte[] bytes,
            String punchType) {
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A site selfie photo is required");
        }
        if (bytes.length > 2_000_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selfie must be smaller than 2 MB");
        }
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selfie must be an image");
        }
        String storedPunch = punchType == null || punchType.isBlank() ? "CHECK_IN" : punchType;
        AttendanceSelfie selfie = selfieRepository
                .findByEmployeeIdAndAttendanceDateAndPunchType(employeeId, date, storedPunch)
                .orElseGet(AttendanceSelfie::new);
        selfie.setEmployeeId(employeeId);
        selfie.setAttendanceDate(date);
        selfie.setPunchType(storedPunch);
        selfie.setContentType(contentType);
        selfie.setImageData(bytes);
        selfie.setCapturedAt(LocalDateTime.now(AttendanceService.COMPANY_ZONE));
        selfieRepository.save(selfie);
        return attendanceRepository.findByEmployee_IdAndAttendanceDate(employeeId, date).orElse(null);
    }

    public List<AttendanceAuditLog> auditTrail(Long employeeId) {
        if (employeeId == null) {
            return auditRepository.findTop200ByOrderByCreatedAtDesc();
        }
        return auditRepository.findTop100ByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    private void applyMonthPenalties(AttendanceRecord record) {
        if (record.getEmployee() == null || record.getEmployee().getId() == null || record.getAttendanceDate() == null) {
            return;
        }
        YearMonth month = YearMonth.from(record.getAttendanceDate());
        List<AttendanceRecord> rows = new ArrayList<>(attendanceRepository
                .findByEmployee_IdAndAttendanceDateBetween(
                        record.getEmployee().getId(),
                        month.atDay(1),
                        month.atEndOfMonth()));
        if (record.getId() != null) {
            rows.removeIf(row -> record.getId().equals(row.getId()));
        }
        rows.add(record);

        int deducted = 0;
        StringBuilder note = new StringBuilder();
        long lateDays = rows.stream().filter(row -> Boolean.TRUE.equals(row.getLateWarning())).count();
        if (lateDays >= AttendanceRules.WARNINGS_BEFORE_PENALTY) {
            deducted += AttendanceRules.LATE_DEDUCT_MINUTES;
            note.append("3 late arrivals: 2 hours deducted. ");
        }
        long yellowDays = rows.stream().filter(row -> Boolean.TRUE.equals(row.getYellowFlag())).count();
        if (yellowDays >= AttendanceRules.WARNINGS_BEFORE_PENALTY) {
            deducted += AttendanceRules.HALF_DAY_MINUTES;
            note.append("3 yellow inactivity flags: half day deducted. ");
        }
        long redDays = rows.stream().filter(row -> Boolean.TRUE.equals(row.getRedFlag())).count();
        if (redDays >= AttendanceRules.WARNINGS_BEFORE_PENALTY) {
            deducted += AttendanceRules.HALF_DAY_MINUTES;
            note.append("3 red lunch-return flags: half day deducted. ");
        }
        int alreadyOnOtherDays = rows.stream()
                .filter(row -> row != record)
                .mapToInt(row -> zero(row.getDeductedMinutes()))
                .sum();
        record.setDeductedMinutes(Math.max(0, deducted - alreadyOnOtherDays));
        record.setPenaltyNote(note.length() == 0 ? null : note.toString().trim());
    }

    private void audit(AttendanceRecord record, Employee actor, String action, String detail) {
        if (record.getEmployee() == null || record.getEmployee().getId() == null) {
            return;
        }
        Employee storedActor = actor;
        if (storedActor != null && storedActor.getId() != null && storedActor.getFullName() == null) {
            storedActor = employeeRepository.findById(storedActor.getId()).orElse(storedActor);
        }
        AttendanceAuditLog log = new AttendanceAuditLog();
        log.setEmployeeId(record.getEmployee().getId());
        log.setActorEmployeeId(storedActor == null ? record.getEmployee().getId() : storedActor.getId());
        log.setActorName(storedActor == null || storedActor.getFullName() == null
                ? "System"
                : storedActor.getFullName());
        log.setAction(action);
        log.setDetail(detail);
        log.setAttendanceDate(record.getAttendanceDate());
        log.setCreatedAt(LocalDateTime.now(AttendanceService.COMPANY_ZONE));
        auditRepository.save(log);
    }

    private static boolean actorIsSiteVisitor(AttendanceRecord record) {
        return record.getEmployee() != null && record.getEmployee().isSiteVisitor();
    }

    private static int idleMinutes(AttendanceRecord record, LocalDateTime now, boolean countChromeIdle) {
        if (!countChromeIdle) {
            return 0;
        }
        int stored = zero(record.getSessionIdleMinutes());
        int trailing = AttendanceRules.countableInactiveMinutes(record.getLastActivityAt(), now);
        if (trailing > AttendanceRules.INACTIVE_LIMIT_MINUTES
                && (now == null || !AttendanceRules.duringInactivityGrace(now.toLocalTime()))) {
            stored += trailing;
        }
        return stored;
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }
}
