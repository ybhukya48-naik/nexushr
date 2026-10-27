-- The Admin on the staff sheet signs in as the website admin
-- with their own email and password. Office staff still report to this person.
UPDATE employees
SET role_type = 'ADMIN'
WHERE employee_code = 'GG-AD-001';
