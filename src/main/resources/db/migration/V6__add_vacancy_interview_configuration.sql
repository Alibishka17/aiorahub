ALTER TABLE job_vacancy ADD COLUMN interview_mode VARCHAR(16);
ALTER TABLE job_vacancy ADD COLUMN interview_language VARCHAR(2);
ALTER TABLE job_vacancy ADD COLUMN interview_intro VARCHAR(2000);
ALTER TABLE job_vacancy ADD COLUMN interview_outro VARCHAR(2000);
ALTER TABLE job_vacancy ADD COLUMN interview_custom_prompt VARCHAR(10000);
ALTER TABLE job_vacancy ADD COLUMN interview_questions_json VARCHAR(12000);
ALTER TABLE job_vacancy ADD COLUMN assessment_criteria_json VARCHAR(12000);
ALTER TABLE job_vacancy ADD COLUMN allow_follow_up_questions BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_vacancy ADD COLUMN allow_question_rephrasing BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_vacancy ADD COLUMN allow_question_reordering BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_vacancy ADD COLUMN allow_question_skipping BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_vacancy ADD COLUMN allow_vacancy_questions BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE job_application ADD COLUMN interview_config_snapshot VARCHAR(30000);
ALTER TABLE job_application ADD COLUMN summary_language VARCHAR(2);
