-- Replace the previous employee list with the Gorle team sheet and the CYOND sheet.
-- Manish is not included. P. Karthik is the super admin. B. Ganesh is HR.
-- Passwords are unset. Each person creates a profile and sets a password.
-- The three Gorle interns have no ID on the sheet, so they use INT-SABEER, INT-NASARA, and INT-HANUMA.

UPDATE employees SET reporting_manager_id = NULL;

UPDATE attendance_audit_logs SET actor_employee_id = NULL;

DELETE FROM attendance_selfies;
DELETE FROM attendance_audit_logs;
DELETE FROM attendance_records;
DELETE FROM leave_requests;
DELETE FROM payroll_records;
DELETE FROM notifications;
DELETE FROM employee_documents;
DELETE FROM employee_lifecycle_history;
DELETE FROM performance_feedback;
DELETE FROM performance_goals;
DELETE FROM performance_reviews;
DELETE FROM employees;

INSERT INTO employees (
    employee_code, full_name, email, password, role_type, department, designation,
    joining_date, base_salary, active, company_id, gender, lifecycle_status, site_visitor,
    password_change_required, profile_completed
)
SELECT
    seed.employee_code,
    seed.full_name,
    seed.email,
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',
    seed.role_type,
    seed.department,
    seed.designation,
    DATE '2026-11-19',
    0,
    TRUE,
    companies.id,
    'PREFER_NOT_TO_SAY',
    'ACTIVE',
    FALSE,
    FALSE,
    FALSE
FROM (
    VALUES
        ('CY-HO-001', 'Kaushik', 'kaushik@cyond.com', 'MANAGER', 'Operations', 'Head of Operations', 'CYOND'),
        ('CY-SR-001', 'Manikanta Banda', 'manikanta.banda@cyond.com', 'EMPLOYEE', 'Sales', 'Sales Representative', 'CYOND'),
        ('CY-SR-002', 'Narsimha Reddy', 'narsimha.reddy@cyond.com', 'EMPLOYEE', 'Sales', 'Sales Representative', 'CYOND'),
        ('CY-SR-003', 'Pradeep Kumar', 'pradeep.kumar@cyond.com', 'EMPLOYEE', 'Sales', 'Trainee Sales Representative', 'CYOND'),
        ('CY-JE-001', 'Bharath Kumar', 'bharath.kumar@cyond.com', 'EMPLOYEE', 'Engineering', 'Junior Engineer', 'CYOND'),
        ('CY-SD-001', 'Solomon Chilumula', 'solomon.chilumula@cyond.com', 'EMPLOYEE', 'Software', 'Software Developer', 'CYOND'),
        ('CY-SD-002', 'Sahithya Gande', 'sahithya.gande@cyond.com', 'EMPLOYEE', 'Software', 'Software Developer', 'CYOND'),
        ('CY-SD-003', 'Yakub', 'yakub@cyond.com', 'EMPLOYEE', 'Software', 'Intern', 'CYOND'),
        ('CY-TH-001', 'Prathyush Annepu', 'prathyush.annepu@cyond.com', 'MANAGER', 'Technical', 'Technical Head', 'CYOND'),
        ('CY-SE-001', 'Sitharam', 'sitharam@cyond.com', 'EMPLOYEE', 'Technical', 'Senior Engineer', 'CYOND'),
        ('CY-SE-002', 'Abidh Ali', 'abidh.ali@cyond.com', 'EMPLOYEE', 'Technical', 'Senior Engineer', 'CYOND'),
        ('CY-JE-002', 'Sudheer', 'sudheer@cyond.com', 'EMPLOYEE', 'Technical', 'Junior Engineer', 'CYOND'),
        ('CY-JE-003', 'Ranganadh', 'ranganadh@cyond.com', 'EMPLOYEE', 'Technical', 'Junior Engineer', 'CYOND'),
        ('CY-JE-004', 'Venkatesh', 'venkatesh@cyond.com', 'EMPLOYEE', 'Technical', 'Junior Engineer', 'CYOND'),
        ('CY-JE-005', 'Pavan', 'pavan@cyond.com', 'EMPLOYEE', 'Technical', 'Junior Engineer', 'CYOND'),
        ('CY-CI-001', 'Mohsin', 'mohsin@cyond.com', 'EMPLOYEE', 'Technical', 'Civil Engineering Intern', 'CYOND'),
        ('CY-CI-002', 'Rakesh', 'rakesh@cyond.com', 'EMPLOYEE', 'Technical', 'Civil Engineering Intern', 'CYOND'),
        ('CY-CI-003', 'Sunil', 'sunil.intern@cyond.com', 'EMPLOYEE', 'Technical', 'Civil Engineering Intern', 'CYOND'),
        ('CY-JE-006', 'Kalyan Reddy', 'kalyan.reddy@cyond.com', 'EMPLOYEE', 'Technical', 'Junior Engineer', 'CYOND'),

        ('310353', 'Krishna Rao', '310353@gorle.com', 'MANAGER', 'TEAM-A', 'HOD', 'GORLE'),
        ('310526', 'Gundu Ashwini', '310526@gorle.com', 'EMPLOYEE', 'TEAM-A', 'Jr. Draftsman', 'GORLE'),
        ('260101', 'Abdul Aziz Mohd', '260101@gorle.com', 'MANAGER', 'TEAM-B', 'Team leader', 'GORLE'),
        ('310407', 'Naga Bhavani Kolusu', '310407@gorle.com', 'EMPLOYEE', 'TEAM-B', 'Design engineer', 'GORLE'),
        ('310505', 'PKN Ashok Kumar', '310505@gorle.com', 'EMPLOYEE', 'TEAM-B', 'Design engineer', 'GORLE'),
        ('230801', 'Hashim Mohammed', '230801@gorle.com', 'EMPLOYEE', 'TEAM-B', 'Sr. Draftsman', 'GORLE'),
        ('INT-SABEER', 'Mohammad Sabeer', 'int.sabeer@gorle.com', 'EMPLOYEE', 'TEAM-B', 'Sr. Draftsman', 'GORLE'),
        ('310521', 'Bandoju Amarendar', '310521@gorle.com', 'EMPLOYEE', 'TEAM-B', 'Design engineer', 'GORLE'),
        ('6001', 'Chandra Shekar (AP Office)', '6001@gorle.com', 'MANAGER', 'TEAM-D', 'Team leader', 'GORLE'),
        ('231910', 'Sharanya', '231910@gorle.com', 'EMPLOYEE', 'TEAM-D', 'Sr. Draftsman', 'GORLE'),
        ('261101', 'Kalyan P', '261101@gorle.com', 'MANAGER', 'TEAM-E', 'Team leader', 'GORLE'),
        ('310362', 'Venket Varun', '310362@gorle.com', 'EMPLOYEE', 'TEAM-E', 'Design engineer', 'GORLE'),
        ('310379', 'Mohammed Shamoon', '310379@gorle.com', 'EMPLOYEE', 'TEAM-E', 'Sr. Draftsman', 'GORLE'),
        ('INT-NASARA', 'Edara Nasara Babu', 'int.nasara@gorle.com', 'EMPLOYEE', 'TEAM-E', 'Jr. Engineer', 'GORLE'),
        ('310524', 'Lingamurla Swathi', '310524@gorle.com', 'EMPLOYEE', 'TEAM-E', 'Jr. Draftsman', 'GORLE'),
        ('INT-HANUMA', 'Hanuma Naik', 'int.hanuma@gorle.com', 'EMPLOYEE', 'TEAM-E', 'Jr. Draftsman', 'GORLE'),
        ('310533', 'Dusiyanth', '310533@gorle.com', 'EMPLOYEE', 'TEAM-E', 'Jr. Draftsman', 'GORLE'),
        ('260108', 'Avinash Gareppally', '260108@gorle.com', 'MANAGER', 'TEAM-G', 'Team leader', 'GORLE'),
        ('310405', 'Pavan Kalyan', '310405@gorle.com', 'EMPLOYEE', 'TEAM-G', 'Jr. Engineer', 'GORLE'),
        ('310537', 'Vinita Pawar', '310537@gorle.com', 'EMPLOYEE', 'TEAM-G', 'Jr. Engineer', 'GORLE'),
        ('310525', 'Sravanthi', '310525@gorle.com', 'EMPLOYEE', 'TEAM-G', 'Jr. Draftsman', 'GORLE'),
        ('310532', 'Vemula Divya', '310532@gorle.com', 'EMPLOYEE', 'TEAM-G', 'Jr. Draftsman', 'GORLE'),
        ('120601', 'Faheem M.A', '120601@gorle.com', 'EMPLOYEE', 'Architecture', 'Jr. Architect', 'GORLE'),
        ('310504', 'Syed Klaja Abrar', '310504@gorle.com', 'EMPLOYEE', 'Architecture', 'Jr. Architect', 'GORLE'),
        ('310304', 'B. Ganesh', 'b.ganesh@gorlegroup.com', 'HR', 'Admin', 'Admin', 'GORLE'),
        ('310310', 'P. Karthik', 'p.karthik@gorlegroup.com', 'SUPER_ADMIN', 'Admin', 'Business Development', 'GORLE')
) AS seed(employee_code, full_name, email, role_type, department, designation, company_code)
JOIN companies ON companies.code = seed.company_code;

UPDATE employees AS staff
SET reporting_manager_id = manager.id
FROM (
    VALUES
        ('CY-SR-001', 'CY-HO-001'),
        ('CY-SR-002', 'CY-HO-001'),
        ('CY-SR-003', 'CY-HO-001'),
        ('CY-JE-001', 'CY-HO-001'),
        ('CY-SD-001', 'CY-HO-001'),
        ('CY-SD-002', 'CY-HO-001'),
        ('CY-SD-003', 'CY-HO-001'),
        ('CY-SE-001', 'CY-TH-001'),
        ('CY-SE-002', 'CY-TH-001'),
        ('CY-JE-002', 'CY-TH-001'),
        ('CY-JE-003', 'CY-TH-001'),
        ('CY-JE-004', 'CY-TH-001'),
        ('CY-JE-005', 'CY-TH-001'),
        ('CY-CI-001', 'CY-TH-001'),
        ('CY-CI-002', 'CY-TH-001'),
        ('CY-CI-003', 'CY-TH-001'),
        ('CY-JE-006', 'CY-TH-001'),

        ('310353', '310310'),
        ('260101', '310310'),
        ('6001', '310310'),
        ('261101', '310310'),
        ('260108', '310310'),
        ('120601', '310310'),
        ('310504', '310310'),
        ('310304', '310310'),
        ('310526', '310353'),
        ('310407', '260101'),
        ('310505', '260101'),
        ('230801', '260101'),
        ('INT-SABEER', '260101'),
        ('310521', '260101'),
        ('231910', '6001'),
        ('310362', '261101'),
        ('310379', '261101'),
        ('INT-NASARA', '261101'),
        ('310524', '261101'),
        ('INT-HANUMA', '261101'),
        ('310533', '261101'),
        ('310405', '260108'),
        ('310537', '260108'),
        ('310525', '260108'),
        ('310532', '260108')
) AS link(staff_code, manager_code)
JOIN employees AS manager ON manager.employee_code = link.manager_code
WHERE staff.employee_code = link.staff_code;
