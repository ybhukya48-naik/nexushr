-- Replace every employee record with the Gorle and Cyond sheets.
-- Struck-through internship names and the crossed-out Cyond block on the Gorle sheet are not included.
-- Login emails are built from the printed names because the sheets have no email column.
-- Password is unset. Each person creates a password from the sign-in page.

UPDATE employees SET reporting_manager_id = NULL;

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
    joining_date, base_salary, active, company_id, gender, lifecycle_status, site_visitor
)
SELECT
    seed.employee_code,
    seed.full_name,
    seed.email,
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH',
    seed.role_type,
    seed.department,
    seed.designation,
    DATE '2026-10-26',
    0,
    TRUE,
    companies.id,
    'PREFER_NOT_TO_SAY',
    'ACTIVE',
    FALSE
FROM (
    VALUES
        ('CY-HO-001', 'Kaushik', 'kaushik@cyond.com', 'MANAGER', 'Operations', 'Head of Operations', 'CYOND'),
        ('CY-HO-002', 'Manish', 'manish@cyond.com', 'MANAGER', 'Operations', 'Head of Operations', 'CYOND'),
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
        ('CY-SE-003', 'Sudheer', 'sudheer@cyond.com', 'EMPLOYEE', 'Technical', 'Senior Engineer', 'CYOND'),
        ('CY-JE-002', 'Ranganadh', 'ranganadh@cyond.com', 'EMPLOYEE', 'Technical', 'Junior Engineer', 'CYOND'),
        ('CY-JE-003', 'Venkatesh', 'venkatesh@cyond.com', 'EMPLOYEE', 'Technical', 'Junior Engineer', 'CYOND'),
        ('CY-JE-004', 'Pavan', 'pavan@cyond.com', 'EMPLOYEE', 'Technical', 'Junior Engineer', 'CYOND'),
        ('CY-JE-005', 'Mohsin', 'mohsin@cyond.com', 'EMPLOYEE', 'Technical', 'Junior Engineer', 'CYOND'),
        ('CY-JE-006', 'Kalyan Reddy', 'kalyan.reddy@cyond.com', 'EMPLOYEE', 'Technical', 'Junior Engineer', 'CYOND'),
        ('CY-CI-001', 'Rakesh', 'rakesh@cyond.com', 'EMPLOYEE', 'Civil', 'Civil Engineering Intern', 'CYOND'),
        ('CY-CI-002', 'Sunil', 'sunil.intern@cyond.com', 'EMPLOYEE', 'Civil', 'Civil Engineering Intern', 'CYOND'),

        ('GG-HF-001', 'Sunil', 'sunil@gorlegroup.com', 'MANAGER', 'Founder', 'Head Founder', 'GORLE'),
        ('GG-TA-001', 'Krishna Rao', 'krishna.rao@gorlegroup.com', 'MANAGER', 'TEAM-A', 'HOD', 'GORLE'),
        ('GG-TA-002', 'Gundu Ashwini', 'gundu.ashwini@gorlegroup.com', 'EMPLOYEE', 'TEAM-A', 'Jr. Draftsman', 'GORLE'),
        ('GG-TB-001', 'Akdul Aziz Mohd', 'akdul.aziz.mohd@gorlegroup.com', 'MANAGER', 'TEAM-B', 'Team Leader', 'GORLE'),
        ('GG-TB-002', 'Naga Bhavani Kolusu', 'naga.bhavani.kolusu@gorlegroup.com', 'EMPLOYEE', 'TEAM-B', 'Design Engineer', 'GORLE'),
        ('GG-TB-003', 'D.K.V Ashok Kumar', 'dkv.ashok.kumar@gorlegroup.com', 'EMPLOYEE', 'TEAM-B', 'Design Engineer', 'GORLE'),
        ('GG-TB-004', 'Hashim Mohammed', 'hashim.mohammed@gorlegroup.com', 'EMPLOYEE', 'TEAM-B', 'Sr. Draftsman', 'GORLE'),
        ('GG-TB-005', 'Mohammad Sabeer', 'mohammad.sabeer@gorlegroup.com', 'EMPLOYEE', 'TEAM-B', 'Sr. Draftsman', 'GORLE'),
        ('GG-TB-006', 'Bardoju Amarendar', 'bardoju.amarendar@gorlegroup.com', 'EMPLOYEE', 'TEAM-B', 'Design Engineer', 'GORLE'),
        ('GG-TD-001', 'Chandra Shekar', 'chandra.shekar@gorlegroup.com', 'MANAGER', 'TEAM-D', 'Team Leader, AP Office', 'GORLE'),
        ('GG-TD-002', 'Sharanya', 'sharanya@gorlegroup.com', 'EMPLOYEE', 'TEAM-D', 'Sr. Draftsman', 'GORLE'),
        ('GG-TE-001', 'Kalyan P', 'kalyan.p@gorlegroup.com', 'MANAGER', 'TEAM-E', 'Team Leader', 'GORLE'),
        ('GG-TE-002', 'Venkat Varun', 'venkat.varun@gorlegroup.com', 'EMPLOYEE', 'TEAM-E', 'Design Engineer', 'GORLE'),
        ('GG-TE-003', 'Mohammed Shamoon', 'mohammed.shamoon@gorlegroup.com', 'EMPLOYEE', 'TEAM-E', 'Sr. Draftsman', 'GORLE'),
        ('GG-TE-004', 'Edura Nasara Babu', 'edura.nasara.babu@gorlegroup.com', 'EMPLOYEE', 'TEAM-E', 'Jr. Engineer', 'GORLE'),
        ('GG-TE-005', 'Lingamarla Swathi', 'lingamarla.swathi@gorlegroup.com', 'EMPLOYEE', 'TEAM-E', 'Jr. Draftsman', 'GORLE'),
        ('GG-TE-006', 'Hanuma Naik', 'hanuma.naik@gorlegroup.com', 'EMPLOYEE', 'TEAM-E', 'Jr. Draftsman', 'GORLE'),
        ('GG-TE-007', 'Dushyanth', 'dushyanth@gorlegroup.com', 'EMPLOYEE', 'TEAM-E', 'Jr. Draftsman', 'GORLE'),
        ('GG-TG-001', 'Avinash Gareppally', 'avinash.gareppally@gorlegroup.com', 'MANAGER', 'TEAM-G', 'Team Leader', 'GORLE'),
        ('GG-TG-002', 'Pavan Kalyan', 'pavan.kalyan@gorlegroup.com', 'EMPLOYEE', 'TEAM-G', 'Jr. Engineer', 'GORLE'),
        ('GG-TG-003', 'Vinita Pawar', 'vinita.pawar@gorlegroup.com', 'EMPLOYEE', 'TEAM-G', 'Jr. Engineer', 'GORLE'),
        ('GG-TG-004', 'Sravanthi', 'sravanthi@gorlegroup.com', 'EMPLOYEE', 'TEAM-G', 'Jr. Draftsman', 'GORLE'),
        ('GG-TG-005', 'Vemula Divya', 'vemula.divya@gorlegroup.com', 'EMPLOYEE', 'TEAM-G', 'Jr. Draftsman', 'GORLE'),
        ('GG-AR-001', 'Faheem M.A', 'faheem.ma@gorlegroup.com', 'EMPLOYEE', 'Architecture', 'Jr. Architect', 'GORLE'),
        ('GG-AR-002', 'Syed Khaja Abrar', 'syed.khaja.abrar@gorlegroup.com', 'EMPLOYEE', 'Architecture', 'Jr. Architect', 'GORLE'),
        ('GG-AD-001', 'B. Ganesh', 'b.ganesh@gorlegroup.com', 'MANAGER', 'Admin', 'Admin', 'GORLE'),
        ('GG-AD-002', 'P. Karthik BD', 'p.karthik@gorlegroup.com', 'EMPLOYEE', 'Admin', 'Business Development', 'GORLE'),
        ('GG-OB-001', 'Basa Shekar', 'basa.shekar@gorlegroup.com', 'EMPLOYEE', 'Office', 'Office Boy', 'GORLE'),
        ('GG-OB-002', 'Mallesh', 'mallesh@gorlegroup.com', 'EMPLOYEE', 'Office', 'Office Boy', 'GORLE'),
        ('GG-ST-001', 'Srinivas', 'srinivas@gorlegroup.com', 'EMPLOYEE', 'Site', 'Sr. Engineer', 'GORLE')
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
        ('CY-SE-003', 'CY-TH-001'),
        ('CY-JE-002', 'CY-TH-001'),
        ('CY-JE-003', 'CY-TH-001'),
        ('CY-JE-004', 'CY-TH-001'),
        ('CY-JE-005', 'CY-TH-001'),
        ('CY-JE-006', 'CY-TH-001'),
        ('CY-CI-001', 'CY-TH-001'),
        ('CY-CI-002', 'CY-TH-001'),

        ('GG-TA-001', 'GG-HF-001'),
        ('GG-TB-001', 'GG-HF-001'),
        ('GG-TD-001', 'GG-HF-001'),
        ('GG-TE-001', 'GG-HF-001'),
        ('GG-TG-001', 'GG-HF-001'),
        ('GG-AR-001', 'GG-HF-001'),
        ('GG-AR-002', 'GG-HF-001'),
        ('GG-AD-001', 'GG-HF-001'),
        ('GG-AD-002', 'GG-HF-001'),
        ('GG-ST-001', 'GG-HF-001'),
        ('GG-TA-002', 'GG-TA-001'),
        ('GG-TB-002', 'GG-TB-001'),
        ('GG-TB-003', 'GG-TB-001'),
        ('GG-TB-004', 'GG-TB-001'),
        ('GG-TB-005', 'GG-TB-001'),
        ('GG-TB-006', 'GG-TB-001'),
        ('GG-TD-002', 'GG-TD-001'),
        ('GG-TE-002', 'GG-TE-001'),
        ('GG-TE-003', 'GG-TE-001'),
        ('GG-TE-004', 'GG-TE-001'),
        ('GG-TE-005', 'GG-TE-001'),
        ('GG-TE-006', 'GG-TE-001'),
        ('GG-TE-007', 'GG-TE-001'),
        ('GG-TG-002', 'GG-TG-001'),
        ('GG-TG-003', 'GG-TG-001'),
        ('GG-TG-004', 'GG-TG-001'),
        ('GG-TG-005', 'GG-TG-001'),
        ('GG-OB-001', 'GG-AD-001'),
        ('GG-OB-002', 'GG-AD-001')
) AS link(staff_code, manager_code)
JOIN employees AS manager ON manager.employee_code = link.manager_code
WHERE staff.employee_code = link.staff_code;
