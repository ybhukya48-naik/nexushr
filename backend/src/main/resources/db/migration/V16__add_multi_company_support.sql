CREATE TABLE companies (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    tagline VARCHAR(255),
    logo_path VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_companies_code UNIQUE (code)
);

INSERT INTO companies (code, name, tagline, logo_path, active)
VALUES
    ('CYOND', 'CYOND', 'Waterproofing Diagnosis & Repair Experts', '/images/cyond-logo.jpeg', TRUE),
    ('GORLE', 'GORLE GROUP', 'STRUCTURAL ENGINEERING', '/images/gorle-group-logo.jpeg', TRUE);

ALTER TABLE employees
    ADD COLUMN company_id BIGINT;

UPDATE employees
SET company_id = (
    SELECT id FROM companies WHERE code = 'CYOND'
)
WHERE company_id IS NULL;

ALTER TABLE employees
    ALTER COLUMN company_id SET NOT NULL;

ALTER TABLE employees
    ADD CONSTRAINT fk_employees_company
    FOREIGN KEY (company_id) REFERENCES companies(id);

CREATE INDEX idx_employees_company_id
    ON employees(company_id);
