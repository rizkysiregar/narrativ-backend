-- Accounts an admin creates from App management (POST /admin/users) to fill the app with first content before real
-- users arrive. Flagged so they can be found, banned or removed later; every account so far signed up by itself.
ALTER TABLE users ADD COLUMN IF NOT EXISTS seeded BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_users_seeded ON users (seeded) WHERE seeded;
