-- Prathyush Annepu is a manager, the same as the other heads.

UPDATE employees
SET role_type = 'MANAGER'
WHERE employee_code = 'CY-TH-001'
   OR lower(email) = 'prathyush.annepu@cyond.com';
