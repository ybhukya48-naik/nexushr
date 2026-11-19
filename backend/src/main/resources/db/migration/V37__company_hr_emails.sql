-- Gorle HR mailbox is hr@gorlegroup.com on B. Ganesh (code 310304).
-- CYOND HR mailbox is hr@cyond.com. Both open the HR dashboard.
-- Passwords stay unset until Create your profile.

UPDATE employees
SET email = 'hr@gorlegroup.com'
WHERE employee_code = '310304';

INSERT INTO employees (
    employee_code, full_name, email, password, role_type, department, designation,
    joining_date, base_salary, active, company_id, gender, lifecycle_status, site_visitor,
    password_change_required, profile_completed
)
SELECT
    'CY-HR-001',
    'CYOND HR',
    'hr@cyond.com',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',
    'HR',
    'HR',
    'HR',
    DATE '2026-11-19',
    0,
    TRUE,
    companies.id,
    'PREFER_NOT_TO_SAY',
    'ACTIVE',
    FALSE,
    FALSE,
    FALSE
FROM companies
WHERE companies.code = 'CYOND'
  AND NOT EXISTS (
      SELECT 1
      FROM employees
      WHERE lower(email) = 'hr@cyond.com'
         OR employee_code = 'CY-HR-001'
  );

UPDATE employees AS staff
SET reporting_manager_id = manager.id
FROM employees AS manager
WHERE staff.employee_code = 'CY-HR-001'
  AND manager.employee_code = 'CY-HO-001'
  AND staff.reporting_manager_id IS NULL;
