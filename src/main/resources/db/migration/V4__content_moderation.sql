-- takedown is separate from is_deleted: is_deleted belongs to the owner (his delete also removes the media),
-- moderation_status belongs to admin moderation and keeps the media so the content can be restored
ALTER TABLE posts
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN moderated_at      TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN moderated_by      UUID,
    ADD COLUMN moderation_reason VARCHAR(500);

ALTER TABLE comments
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN moderated_at      TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN moderated_by      UUID,
    ADD COLUMN moderation_reason VARCHAR(500);

CREATE INDEX idx_posts_moderation_status    ON posts (moderation_status);
CREATE INDEX idx_comments_moderation_status ON comments (moderation_status);
