DROP INDEX IF EXISTS uk_company_holiday_day;

CREATE UNIQUE INDEX uk_company_holiday_day
    ON company_holidays (holiday_date, COALESCE(company_code, ''));
