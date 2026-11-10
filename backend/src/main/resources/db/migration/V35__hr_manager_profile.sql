-- HR and managers sign in with their own password.
-- The next sign-in asks them to create a profile. They stay off the employee list.

UPDATE employees
SET profile_completed = FALSE
WHERE role_type IN ('HR', 'MANAGER');
