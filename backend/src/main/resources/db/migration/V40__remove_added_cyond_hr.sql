-- The CYOND HR mailbox was added for a check. It is not on the staff sheet.

UPDATE employees
SET reporting_manager_id = NULL
WHERE reporting_manager_id IN (
    SELECT id FROM employees WHERE employee_code = 'CY-HR-001'
);

UPDATE attendance_audit_logs
SET actor_employee_id = NULL
WHERE actor_employee_id IN (
    SELECT id FROM employees WHERE employee_code = 'CY-HR-001'
);

DELETE FROM attendance_selfies
WHERE employee_id IN (SELECT id FROM employees WHERE employee_code = 'CY-HR-001');

DELETE FROM attendance_audit_logs
WHERE employee_id IN (SELECT id FROM employees WHERE employee_code = 'CY-HR-001');

DELETE FROM attendance_records
WHERE employee_id IN (SELECT id FROM employees WHERE employee_code = 'CY-HR-001');

DELETE FROM leave_requests
WHERE employee_id IN (SELECT id FROM employees WHERE employee_code = 'CY-HR-001');

DELETE FROM payroll_records
WHERE employee_id IN (SELECT id FROM employees WHERE employee_code = 'CY-HR-001');

DELETE FROM notifications
WHERE employee_id IN (SELECT id FROM employees WHERE employee_code = 'CY-HR-001');

DELETE FROM employee_documents
WHERE employee_id IN (SELECT id FROM employees WHERE employee_code = 'CY-HR-001');

DELETE FROM employee_lifecycle_history
WHERE employee_id IN (SELECT id FROM employees WHERE employee_code = 'CY-HR-001');

DELETE FROM performance_feedback
WHERE employee_id IN (SELECT id FROM employees WHERE employee_code = 'CY-HR-001')
   OR reviewer_id IN (SELECT id FROM employees WHERE employee_code = 'CY-HR-001');

DELETE FROM performance_goals
WHERE employee_id IN (SELECT id FROM employees WHERE employee_code = 'CY-HR-001');

DELETE FROM performance_reviews
WHERE employee_id IN (SELECT id FROM employees WHERE employee_code = 'CY-HR-001');

DELETE FROM employees
WHERE employee_code = 'CY-HR-001';
