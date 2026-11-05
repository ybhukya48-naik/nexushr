ALTER TABLE leave_requests
    ADD COLUMN IF NOT EXISTS leave_type VARCHAR(20);

ALTER TABLE leave_requests
    ADD COLUMN IF NOT EXISTS half_day BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE leave_requests
SET leave_type = 'EARNED'
WHERE leave_type IS NULL;
