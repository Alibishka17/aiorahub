ALTER TABLE job_vacancy ADD COLUMN category VARCHAR(120);
ALTER TABLE job_vacancy ADD COLUMN city VARCHAR(120);
ALTER TABLE job_vacancy ADD COLUMN country VARCHAR(120);
ALTER TABLE job_vacancy ADD COLUMN salary_min BIGINT;
ALTER TABLE job_vacancy ADD COLUMN salary_max BIGINT;
ALTER TABLE job_vacancy ADD COLUMN salary_currency VARCHAR(10);
ALTER TABLE job_vacancy ADD COLUMN required_documents VARCHAR(3000);
ALTER TABLE job_vacancy ADD COLUMN additional_info VARCHAR(3000);
ALTER TABLE job_vacancy ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_job_vacancy_category ON job_vacancy (category);
CREATE INDEX idx_job_vacancy_city ON job_vacancy (city);

UPDATE job_vacancy
SET category = 'IT', city = 'Berlin', country = 'Germany', salary_min = 65000, salary_max = 80000, salary_currency = 'EUR'
WHERE LOWER(title) LIKE '%java%' AND (LOWER(title) LIKE '%berlin%' OR LOWER(title) LIKE '%берлин%');

UPDATE job_vacancy
SET category = 'Healthcare', city = 'Munich', country = 'Germany', salary_min = 3200, salary_currency = 'EUR'
WHERE LOWER(title) LIKE '%nurse%' OR LOWER(title) LIKE '%медсестр%';

UPDATE job_vacancy
SET category = 'Engineering', city = 'Hamburg', country = 'Germany', salary_min = 4000, salary_currency = 'EUR'
WHERE LOWER(title) LIKE '%electric%' OR LOWER(title) LIKE '%электрик%';

UPDATE job_vacancy
SET category = 'IT'
WHERE category IS NULL AND LOWER(title) LIKE '%devops%';
