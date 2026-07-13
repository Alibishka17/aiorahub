ALTER TABLE job_application ALTER COLUMN candidate_id DROP NOT NULL;

ALTER TABLE job_application ADD COLUMN guest_first_name VARCHAR(255);
ALTER TABLE job_application ADD COLUMN guest_last_name VARCHAR(255);
ALTER TABLE job_application ADD COLUMN guest_email VARCHAR(255);
ALTER TABLE job_application ADD COLUMN guest_phone VARCHAR(32);
ALTER TABLE job_application ADD COLUMN guest_telegram_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_application ADD COLUMN guest_whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_application ADD COLUMN guest_access_token VARCHAR(64);

CREATE UNIQUE INDEX idx_job_application_guest_token ON job_application (guest_access_token);
CREATE INDEX idx_job_application_guest_email ON job_application (vacancy_id, guest_email);
