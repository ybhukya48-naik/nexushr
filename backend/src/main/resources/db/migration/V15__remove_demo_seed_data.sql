-- Remove demo/seed employee data created by V2__seed_data.sql.
-- Keep the original V2 migration unchanged so Flyway checksums remain valid.

DELETE FROM employee_documents
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE employee_code IN ('admin', 'hr', 'manager', 'employee', 'E1001', 'E1002', 'E1003')
);

DELETE FROM employee_lifecycle_history
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE employee_code IN ('admin', 'hr', 'manager', 'employee', 'E1001', 'E1002', 'E1003')
);

DELETE FROM performance_feedback
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE employee_code IN ('admin', 'hr', 'manager', 'employee', 'E1001', 'E1002', 'E1003')
)
OR reviewer_id IN (
    SELECT id FROM employees
    WHERE employee_code IN ('admin', 'hr', 'manager', 'employee', 'E1001', 'E1002', 'E1003')
);

DELETE FROM performance_goals
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE employee_code IN ('admin', 'hr', 'manager', 'employee', 'E1001', 'E1002', 'E1003')
);

DELETE FROM performance_reviews
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE employee_code IN ('admin', 'hr', 'manager', 'employee', 'E1001', 'E1002', 'E1003')
);

DELETE FROM payroll_records
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE employee_code IN ('admin', 'hr', 'manager', 'employee', 'E1001', 'E1002', 'E1003')
);

DELETE FROM attendance_records
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE employee_code IN ('admin', 'hr', 'manager', 'employee', 'E1001', 'E1002', 'E1003')
);

DELETE FROM leave_requests
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE employee_code IN ('admin', 'hr', 'manager', 'employee', 'E1001', 'E1002', 'E1003')
);

DELETE FROM notifications
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE employee_code IN ('admin', 'hr', 'manager', 'employee', 'E1001', 'E1002', 'E1003')
);

DELETE FROM employees
WHERE employee_code IN ('admin', 'hr', 'manager', 'employee', 'E1001', 'E1002', 'E1003');
