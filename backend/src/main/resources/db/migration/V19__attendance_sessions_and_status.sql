ALTER TABLE attendance_records
    ADD COLUMN session_started_at TIMESTAMP,
    ADD COLUMN closed_work_minutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN break_minutes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN attendance_status VARCHAR(20);
