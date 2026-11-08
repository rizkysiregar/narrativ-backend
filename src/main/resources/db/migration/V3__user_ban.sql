-- ban is separate from is_deleted: is_deleted belongs to the user (self delete), status/ban_* belong to admin moderation
ALTER TABLE users
    ADD COLUMN status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN banned_at  TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN banned_by  UUID,
    ADD COLUMN ban_reason VARCHAR(500);

CREATE INDEX idx_users_status ON users (status);
