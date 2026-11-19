-- The in-app inbox: what happened to you (a like, a comment, a reply, a follow, a pin). The old user_notification
-- table was never written (no repository, no endpoint), so it is replaced rather than altered.
DROP TABLE IF EXISTS user_notification;

CREATE TABLE user_notification (
    id           UUID                        PRIMARY KEY,
    recipient_id UUID                        NOT NULL REFERENCES users (id),
    type         VARCHAR(30)                 NOT NULL,
    actor_id     UUID                        NOT NULL REFERENCES users (id),
    post_id      UUID                        REFERENCES posts (id),
    -- a deleted comment takes its notifications along
    comment_id   UUID                        REFERENCES comments (id) ON DELETE CASCADE,
    preview      VARCHAR(200),
    -- one row per thing that happened: a redelivered event finds its row and adds nothing
    dedup_key    VARCHAR(160)                NOT NULL,
    -- rows with the same key show as one entry ("ani and 3 others liked your post")
    group_key    VARCHAR(160)                NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    read_at      TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_user_notification_dedup UNIQUE (dedup_key)
);

CREATE INDEX idx_user_notification_recipient ON user_notification (recipient_id, created_at DESC);
CREATE INDEX idx_user_notification_unread ON user_notification (recipient_id) WHERE read_at IS NULL;
CREATE INDEX idx_user_notification_created ON user_notification (created_at);
