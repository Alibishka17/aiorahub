ALTER TABLE users ADD COLUMN first_name VARCHAR(255);
ALTER TABLE users ADD COLUMN last_name VARCHAR(255);
ALTER TABLE users ADD COLUMN phone VARCHAR(32);
ALTER TABLE users ADD COLUMN telegram_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN cv_file_name VARCHAR(255);
ALTER TABLE users ADD COLUMN cv_content_type VARCHAR(120);
ALTER TABLE users ADD COLUMN cv_storage_key VARCHAR(255);

ALTER TABLE job_vacancy ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE job_vacancy ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE job_application ADD COLUMN recruiter_message VARCHAR(3000);
ALTER TABLE job_application ADD COLUMN confirmed_at TIMESTAMP;

ALTER TABLE interview_result ADD COLUMN summary VARCHAR(3000);
ALTER TABLE interview_result ADD COLUMN transcript VARCHAR(12000);
ALTER TABLE interview_result ADD COLUMN conclusion VARCHAR(3000);

CREATE INDEX idx_job_vacancy_employer_status ON job_vacancy (employer_id, status);
CREATE UNIQUE INDEX idx_job_application_unique_candidate ON job_application (vacancy_id, candidate_id);
