ALTER TABLE payroll_records
ADD COLUMN tax_amount DECIMAL(15,2);

ALTER TABLE payroll_records
ADD COLUMN other_deductions DECIMAL(15,2);

ALTER TABLE payroll_records
ADD COLUMN status VARCHAR(30);

UPDATE payroll_records
SET tax_amount = deductions,
    other_deductions = 0,
    status = 'GENERATED';

ALTER TABLE payroll_records
ALTER COLUMN tax_amount SET NOT NULL;

ALTER TABLE payroll_records
ALTER COLUMN other_deductions SET NOT NULL;

ALTER TABLE payroll_records
ALTER COLUMN status SET NOT NULL;

ALTER TABLE payroll_records
ADD CONSTRAINT uk_payroll_employee_month
UNIQUE (employee_id, pay_month);
