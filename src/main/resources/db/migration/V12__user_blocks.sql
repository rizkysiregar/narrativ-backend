-- a block hides two users from each other (posts, comments, search, follow lists) until the blocker undoes it
CREATE TABLE user_blocks (
    id         UUID                        PRIMARY KEY,
    blocker_id UUID                        NOT NULL REFERENCES users (id),
    blocked_id UUID                        NOT NULL REFERENCES users (id),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_blocks_pair UNIQUE (blocker_id, blocked_id),
    CONSTRAINT ck_user_blocks_not_self CHECK (blocker_id <> blocked_id)
);

-- the unique constraint already serves lookups by blocker; this one serves "who blocked me"
CREATE INDEX idx_user_blocks_blocked ON user_blocks (blocked_id, blocker_id);
