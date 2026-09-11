ALTER TABLE employees
ADD COLUMN gender VARCHAR(30);

UPDATE employees
SET gender = CASE
    WHEN employee_code IN ('admin', 'manager', 'employee', 'E1001') THEN 'MALE'
    WHEN employee_code IN ('hr', 'E1002') THEN 'FEMALE'
    WHEN employee_code = 'E1003' THEN 'MALE'
    ELSE 'PREFER_NOT_TO_SAY'
END
WHERE gender IS NULL;

ALTER TABLE employees
ALTER COLUMN gender SET NOT NULL;

CREATE INDEX idx_employees_gender
ON employees(gender);

CREATE INDEX idx_employees_joining_date
ON employees(joining_date);

CREATE INDEX idx_employees_lifecycle_status
ON employees(lifecycle_status);
