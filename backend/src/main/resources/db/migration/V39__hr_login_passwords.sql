-- Both company HR mailboxes sign in on HR login.
-- hr@gorlegroup.com is B. Ganesh. hr@cyond.com is the CYOND HR account.

UPDATE employees
SET role_type = 'HR',
    password = '$2a$10$ZtzmGj0kifxe9C2m4uM15OD5yWp6jfDAZgyxnGKY./MlK7AUcXWxe',
    password_change_required = FALSE,
    profile_completed = TRUE
WHERE lower(email) IN ('hr@gorlegroup.com', 'hr@cyond.com');
