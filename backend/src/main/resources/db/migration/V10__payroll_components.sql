ALTER TABLE payroll_records
ADD COLUMN basic_salary DECIMAL(15,2);

ALTER TABLE payroll_records
ADD COLUMN hra DECIMAL(15,2);

ALTER TABLE payroll_records
ADD COLUMN bonus DECIMAL(15,2);

ALTER TABLE payroll_records
ADD COLUMN overtime DECIMAL(15,2);

ALTER TABLE payroll_records
ADD COLUMN pf DECIMAL(15,2);

ALTER TABLE payroll_records
ADD COLUMN leave_deduction DECIMAL(15,2);

UPDATE payroll_records
SET basic_salary = gross_salary,
    hra = 0,
    bonus = 0,
    overtime = 0,
    pf = 0,
    leave_deduction = 0;

ALTER TABLE payroll_records
ALTER COLUMN basic_salary SET NOT NULL;

ALTER TABLE payroll_records
ALTER COLUMN hra SET NOT NULL;

ALTER TABLE payroll_records
ALTER COLUMN bonus SET NOT NULL;

ALTER TABLE payroll_records
ALTER COLUMN overtime SET NOT NULL;

ALTER TABLE payroll_records
ALTER COLUMN pf SET NOT NULL;

ALTER TABLE payroll_records
ALTER COLUMN leave_deduction SET NOT NULL;
