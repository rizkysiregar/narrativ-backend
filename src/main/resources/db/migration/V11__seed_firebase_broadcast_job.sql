-- FirebaseSchedulerService reads this row on every run and fails when it is missing, so a new database
-- (production) never sent the scheduled broadcast. Existing databases already have it: nothing changes there.
-- last_executed_at = now: the first broadcast goes out one interval after the first start, not at the first start.
INSERT INTO job_execution_log (job_name, last_executed_at)
VALUES ('firebase_broadcast_job', CURRENT_TIMESTAMP)
ON CONFLICT (job_name) DO NOTHING;
