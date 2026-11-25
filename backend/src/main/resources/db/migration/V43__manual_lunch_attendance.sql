ALTER TABLE attendance_records ADD COLUMN manual_lunch BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE attendance_records ADD COLUMN lunch_started_at TIMESTAMP;
