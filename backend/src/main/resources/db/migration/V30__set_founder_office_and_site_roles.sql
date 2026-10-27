-- These four people have no printed staff ID.
-- Sunil is the founder, Basa Shekar and Mallesh are office boys,
-- and Srinivas is the site engineer.

UPDATE employees
SET designation = 'Founder',
    department = 'Founder'
WHERE employee_code = 'GG-HF-001';

UPDATE employees
SET designation = 'Office Boy',
    department = 'Office'
WHERE employee_code IN ('GG-OB-001', 'GG-OB-002');

UPDATE employees
SET designation = 'Site Engineer',
    department = 'Site'
WHERE employee_code = 'GG-ST-001';
