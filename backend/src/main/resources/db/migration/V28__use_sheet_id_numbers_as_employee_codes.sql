-- The Gorle staff sheet prints an ID number for each person.
-- Those numbers are the employee codes. Three interns have no ID on the sheet,
-- so their existing codes stay as they are.

UPDATE employees AS staff
SET employee_code = sheet.id_no
FROM (
    VALUES
        ('GG-TA-001', '310353'),
        ('GG-TA-002', '310526'),
        ('GG-TB-001', '260101'),
        ('GG-TB-002', '310407'),
        ('GG-TB-003', '310505'),
        ('GG-TB-004', '230801'),
        ('GG-TB-006', '310521'),
        ('GG-TD-001', '6001'),
        ('GG-TD-002', '231910'),
        ('GG-TE-001', '261101'),
        ('GG-TE-002', '310362'),
        ('GG-TE-003', '310379'),
        ('GG-TE-005', '310524'),
        ('GG-TE-007', '310533'),
        ('GG-TG-001', '260108'),
        ('GG-TG-002', '310405'),
        ('GG-TG-003', '310537'),
        ('GG-TG-004', '310525'),
        ('GG-TG-005', '310532'),
        ('GG-AR-001', '120601'),
        ('GG-AR-002', '310504'),
        ('GG-AD-001', '310304'),
        ('GG-AD-002', '310310')
) AS sheet(old_code, id_no)
WHERE staff.employee_code = sheet.old_code;
