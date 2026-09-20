UPDATE employees SET salary_currency = 'INR' WHERE salary_currency <> 'INR';
UPDATE payroll_items SET salary_currency = 'INR' WHERE salary_currency <> 'INR';

ALTER TABLE employees ADD CONSTRAINT employees_salary_currency_inr_check
  CHECK (salary_currency = 'INR');
ALTER TABLE payroll_items ADD CONSTRAINT payroll_items_salary_currency_inr_check
  CHECK (salary_currency = 'INR');
