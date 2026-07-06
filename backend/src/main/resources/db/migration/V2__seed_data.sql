INSERT INTO employees (employee_code, full_name, email, password, role_type, department, designation, joining_date, base_salary, active)
VALUES
    ('admin', 'Admin User', 'admin@zidio.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'ADMIN', 'Executive', 'System Administrator', '2020-01-01', 200000.00, TRUE),
    ('hr', 'HR User', 'hr@zidio.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'HR', 'Human Resources', 'HR Manager', '2021-06-15', 120000.00, TRUE),
    ('manager', 'Manager User', 'manager@zidio.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'MANAGER', 'Engineering', 'Engineering Manager', '2022-03-14', 160000.00, TRUE),
    ('employee', 'Employee User', 'employee@zidio.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'EMPLOYEE', 'Engineering', 'Senior Developer', '2023-02-01', 110000.00, TRUE),
    ('E1001', 'Aarav Sharma', 'aarav.sharma@zidio.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'MANAGER', 'Engineering', 'Engineering Manager', '2022-03-14', 160000.00, TRUE),
    ('E1002', 'Sara Khan', 'sara.khan@zidio.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'EMPLOYEE', 'Engineering', 'Senior Java Developer', '2023-02-01', 110000.00, TRUE),
    ('E1003', 'Rahul Iyer', 'rahul.iyer@zidio.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'HR', 'Human Resources', 'HR Business Partner', '2021-11-10', 90000.00, TRUE);

INSERT INTO performance_reviews (employee_id, review_year, score, feedback, review_date)
VALUES
    (2, 2025, 78, 'Solid delivery and teamwork.', '2025-12-20'),
    (2, 2024, 72, 'Good progress with minor consistency gaps.', '2024-12-19');
