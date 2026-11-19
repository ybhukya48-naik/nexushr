-- P. Karthik opens the admin panel with employee code 310310.

UPDATE employees
SET role_type = 'SUPER_ADMIN',
    password = '$2a$10$TGfeErMAz4Ja/l8OJjL28u1TS1BOJ49RHKQlqlceKMw85NVt8yHCG',
    password_change_required = FALSE,
    profile_completed = TRUE
WHERE employee_code = '310310';
