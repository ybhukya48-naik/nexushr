package com.zidio.nexushr.service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * One employee day can have several check-in and check-out pairs.
 * Time between a check-out and the next check-in is break, not work.
 */
public final class AttendanceDay {

    private int closedWorkMinutes;
    private int breakMinutes;
    private LocalDateTime firstCheckIn;
    private LocalDateTime lastCheckOut;
    private LocalDateTime sessionStartedAt;
    private String status = "NONE";

    public void restore(
            LocalDateTime firstCheckIn,
            LocalDateTime lastCheckOut,
            LocalDateTime sessionStartedAt,
            int closedWorkMinutes,
            int breakMinutes,
            String status) {
        this.firstCheckIn = firstCheckIn;
        this.lastCheckOut = lastCheckOut;
        this.sessionStartedAt = sessionStartedAt;
        this.closedWorkMinutes = Math.max(0, closedWorkMinutes);
        this.breakMinutes = Math.max(0, breakMinutes);
        this.status = status == null || status.isBlank()
                ? (lastCheckOut != null ? "CLOSED" : firstCheckIn != null ? "OPEN" : "NONE")
                : status;
        if (this.sessionStartedAt == null
                && this.firstCheckIn != null
                && this.lastCheckOut == null
                && !"INACTIVE".equals(this.status)) {
            this.sessionStartedAt = this.firstCheckIn;
        }
    }

    public void checkIn(LocalDateTime now) {
        if (sessionOpen()) {
            throw new IllegalStateException("Already checked in");
        }
        if (lastCheckOut != null) {
            breakMinutes += minutesBetween(lastCheckOut, now);
            lastCheckOut = null;
        }
        if (firstCheckIn == null) {
            firstCheckIn = now;
        }
        sessionStartedAt = now;
        status = "OPEN";
    }

    public void checkOut(LocalDateTime now) {
        if (!sessionOpen() || sessionStartedAt == null) {
            throw new IllegalStateException("Not checked in");
        }
        if (now.isBefore(sessionStartedAt)) {
            throw new IllegalStateException("Check-out time cannot be before check-in time");
        }
        closedWorkMinutes += AttendanceRules.sessionMinutes(sessionStartedAt, now);
        lastCheckOut = now;
        sessionStartedAt = null;
        status = "CLOSED";
    }

    public boolean sessionOpen() {
        return sessionStartedAt != null && lastCheckOut == null;
    }

    public void markInactive() {
        status = "INACTIVE";
    }

    public int closedWorkMinutes() {
        return closedWorkMinutes;
    }

    public int breakMinutes() {
        return breakMinutes;
    }

    public LocalDateTime firstCheckIn() {
        return firstCheckIn;
    }

    public LocalDateTime lastCheckOut() {
        return lastCheckOut;
    }

    public LocalDateTime sessionStartedAt() {
        return sessionStartedAt;
    }

    public String status() {
        return status;
    }

    static int minutesBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) minutes;
    }
}
