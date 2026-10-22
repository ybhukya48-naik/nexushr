ALTER TABLE attendance_selfies
    ADD COLUMN punch_type VARCHAR(20) NOT NULL DEFAULT 'CHECK_IN';

ALTER TABLE attendance_selfies
    DROP CONSTRAINT uk_attendance_selfie_day;

ALTER TABLE attendance_selfies
    ADD CONSTRAINT uk_attendance_selfie_punch
        UNIQUE (employee_id, attendance_date, punch_type);
