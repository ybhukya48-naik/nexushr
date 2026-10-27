-- Yakub signs in to the HR dashboard with his own email.
-- The staff employee yakub@cyond.com stays a separate employee login.

INSERT INTO employees (
    employee_code, full_name, email, password, role_type, department, designation,
    joining_date, base_salary, active, company_id, gender, lifecycle_status, site_visitor
)
SELECT
    'HR-YK-001',
    'Bhukya Yakub',
    'ybhukya@gmail.com',
    '$2a$10$P/5bzPSiTe4naAxNxfLQw.sVRyHqTdKDYB1sbjJ4Q8vAcO/Q96zfi',
    'HR',
    'Human Resources',
    'HR',
    DATE '2026-10-27',
    0,
    TRUE,
    id,
    'PREFER_NOT_TO_SAY',
    'ACTIVE',
    FALSE
FROM companies
WHERE code = 'CYOND'
  AND NOT EXISTS (
      SELECT 1 FROM employees WHERE lower(trim(email)) = 'ybhukya@gmail.com'
  );

UPDATE employees
SET role_type = 'HR',
    full_name = 'Bhukya Yakub',
    department = 'Human Resources',
    designation = 'HR',
    active = TRUE,
    lifecycle_status = 'ACTIVE',
    password = '$2a$10$P/5bzPSiTe4naAxNxfLQw.sVRyHqTdKDYB1sbjJ4Q8vAcO/Q96zfi'
WHERE lower(trim(email)) = 'ybhukya@gmail.com';
