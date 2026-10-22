CREATE INDEX IF NOT EXISTS idx_employees_email_lower
    ON employees (lower(email));

CREATE INDEX IF NOT EXISTS idx_employees_code_lower
    ON employees (lower(employee_code));
