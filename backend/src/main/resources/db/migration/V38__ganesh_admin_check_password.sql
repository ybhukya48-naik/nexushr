-- B. Ganesh (310304) opens the admin panel for a check.
-- The password is temporary. The next screen asks for a new one.

UPDATE employees
SET role_type = 'ADMIN',
    password = '$2a$10$MT3IBg.MObTPvX.9OqzuwuNc4z80VMyIuti1WBbuM3rp370XagxIy',
    password_change_required = TRUE,
    profile_completed = TRUE
WHERE employee_code = '310304';
