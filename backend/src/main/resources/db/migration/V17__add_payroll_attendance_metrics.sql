ALTER TABLE payroll_records
ADD COLUMN expected_work_minutes INTEGER NOT NULL DEFAULT 0;

ALTER TABLE payroll_records
ADD COLUMN worked_minutes INTEGER NOT NULL DEFAULT 0;

ALTER TABLE payroll_records
ADD COLUMN shortfall_minutes INTEGER NOT NULL DEFAULT 0;

ALTER TABLE payroll_records
ADD COLUMN overtime_minutes INTEGER NOT NULL DEFAULT 0;
