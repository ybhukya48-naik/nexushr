ALTER TABLE attendance_records
    ADD COLUMN late_arrival_minutes INTEGER NOT NULL DEFAULT 0;

ALTER TABLE attendance_records
    ADD COLUMN overtime_minutes INTEGER NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_attendance_employee_date
    ON attendance_records(employee_id, attendance_date);

CREATE INDEX idx_attendance_date
    ON attendance_records(attendance_date);

CREATE INDEX idx_attendance_employee_date
    ON attendance_records(employee_id, attendance_date);

CREATE INDEX idx_leave_employee
    ON leave_requests(employee_id);

CREATE INDEX idx_leave_status
    ON leave_requests(status);

CREATE INDEX idx_leave_employee_status
    ON leave_requests(employee_id, status);
