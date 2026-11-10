-- Remove previous employee records and the attendance, leave, pay,
-- and profile rows that belong to them. HR, admin, and super-admin
-- sign-ins stay so the portal can still be opened.

UPDATE employees
SET reporting_manager_id = NULL
WHERE reporting_manager_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
);

UPDATE attendance_audit_logs
SET actor_employee_id = NULL
WHERE actor_employee_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
);

DELETE FROM attendance_selfies
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
);

DELETE FROM attendance_audit_logs
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
);

DELETE FROM attendance_records
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
);

DELETE FROM leave_requests
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
);

DELETE FROM payroll_records
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
);

DELETE FROM performance_feedback
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
)
OR reviewer_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
);

DELETE FROM performance_goals
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
);

DELETE FROM performance_reviews
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
);

DELETE FROM employee_documents
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
);

DELETE FROM employee_lifecycle_history
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
);

DELETE FROM notifications
WHERE employee_id IN (
    SELECT id FROM employees
    WHERE role_type IS NULL
       OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN')
);

DELETE FROM employees
WHERE role_type IS NULL
   OR role_type NOT IN ('HR', 'ADMIN', 'SUPER_ADMIN');
